package dev.airscroll.core.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import dev.airscroll.core.common.model.HandFrame
import dev.airscroll.core.common.model.HandSignal
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.hypot

/**
 * Implementazione basata su MediaPipe Gesture Recognizer.
 *
 * Un solo modello copre entrambi i compiti: riconosce i gesti canonici
 * (pollice in su, pugno chiuso) e restituisce i landmark della mano, che usiamo
 * per seguire il palmo. Tutto in locale, nessuna chiamata di rete.
 */
class MediaPipeHandTracker(
    context: Context,
    private val config: VisionConfig = VisionConfig.Default,
) : HandTracker {

    private val appContext = context.applicationContext

    private val _frames = MutableSharedFlow<HandFrame>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val frames: SharedFlow<HandFrame> = _frames.asSharedFlow()

    override var lastError: String? = null
        private set

    override var usingGpu: Boolean = false
        private set

    private var recognizer: GestureRecognizer? = null

    override val isReady: Boolean
        get() = recognizer != null
    private val busy = AtomicBoolean(false)
    private var lastSubmittedTimestamp = 0L

    @Synchronized
    override fun start() {
        if (recognizer != null) return
        if (config.preferGpu && tryCreate(Delegate.GPU)) {
            usingGpu = true
            return
        }
        usingGpu = false
        if (!tryCreate(Delegate.CPU)) {
            // Il caso tipico e' il modello assente dagli asset. Va detto forte:
            // silenziosamente, l'app sembrerebbe solo cieca.
            Log.e(TAG, "Riconoscitore non inizializzato: $lastError")
        }
    }

    private fun tryCreate(delegate: Delegate): Boolean = try {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(config.modelAssetPath)
            .setDelegate(delegate)
            .build()

        val options = GestureRecognizer.GestureRecognizerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(config.numHands)
            .setMinHandDetectionConfidence(config.minHandDetectionConfidence)
            .setMinHandPresenceConfidence(config.minHandPresenceConfidence)
            .setMinTrackingConfidence(config.minTrackingConfidence)
            .setResultListener { result, _ -> onResult(result) }
            .setErrorListener { error ->
                busy.set(false)
                lastError = error.message
                Log.w(TAG, "Errore MediaPipe", error)
            }
            .build()

        recognizer = GestureRecognizer.createFromOptions(appContext, options)
        lastError = null
        true
    } catch (t: Throwable) {
        // Su alcuni dispositivi il delegate GPU non e' disponibile: e' un caso
        // atteso, non un crash. Si riprova su CPU.
        lastError = t.message ?: t::class.java.simpleName
        Log.w(TAG, "Delegate $delegate non utilizzabile: $lastError")
        recognizer = null
        false
    }

    override fun submit(bitmap: Bitmap, timestampMs: Long) {
        val active = recognizer ?: return
        // MediaPipe in LIVE_STREAM pretende timestamp strettamente crescenti.
        val stamp = if (timestampMs <= lastSubmittedTimestamp) lastSubmittedTimestamp + 1 else timestampMs
        if (!busy.compareAndSet(false, true)) return
        lastSubmittedTimestamp = stamp
        try {
            active.recognizeAsync(BitmapImageBuilder(bitmap).build(), stamp)
        } catch (t: Throwable) {
            busy.set(false)
            lastError = t.message
            Log.w(TAG, "recognizeAsync fallita", t)
        }
    }

    @Synchronized
    override fun stop() {
        busy.set(false)
        lastSubmittedTimestamp = 0L
        runCatching { recognizer?.close() }
        recognizer = null
    }

    private fun onResult(result: GestureRecognizerResult) {
        busy.set(false)
        val timestamp = result.timestampMs()
        val landmarks = result.landmarks().firstOrNull()
        if (landmarks == null || landmarks.size <= MIDDLE_MCP) {
            _frames.tryEmit(HandFrame.absent(timestamp))
            return
        }

        val wrist = landmarks[WRIST]
        val indexMcp = landmarks[INDEX_MCP]
        val middleMcp = landmarks[MIDDLE_MCP]
        val pinkyMcp = landmarks[PINKY_MCP]

        // Centro del palmo: la media di polso e nocche e' molto piu' stabile del
        // solo polso quando le dita si aprono e chiudono.
        val palmX = (wrist.x() + indexMcp.x() + middleMcp.x() + pinkyMcp.x()) / 4f
        val palmY = (wrist.y() + indexMcp.y() + middleMcp.y() + pinkyMcp.y()) / 4f
        val span = hypot(middleMcp.x() - wrist.x(), middleMcp.y() - wrist.y())
        val mirroredX = if (config.mirrorHorizontally) 1f - palmX else palmX

        val topGesture = result.gestures().firstOrNull()?.firstOrNull()
        val score = topGesture?.score() ?: 0f
        val signal = if (score >= config.minGestureConfidence) {
            HandSignal.fromMediaPipeLabel(topGesture?.categoryName())
        } else {
            HandSignal.NONE
        }

        _frames.tryEmit(
            HandFrame(
                timestampMs = timestamp,
                present = true,
                signal = signal,
                signalConfidence = score,
                palmX = mirroredX.coerceIn(0f, 1f),
                palmY = palmY.coerceIn(0f, 1f),
                handSpan = span,
            )
        )
    }

    private companion object {
        const val TAG = "AirScroll/Vision"
        const val WRIST = 0
        const val INDEX_MCP = 5
        const val MIDDLE_MCP = 9
        const val PINKY_MCP = 17
    }
}
