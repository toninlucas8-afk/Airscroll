package dev.airscroll.core.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import dev.airscroll.core.common.model.PerformanceMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Gestisce la fotocamera frontale in sola analisi.
 *
 * Punti chiave per il consumo:
 * - nessuna Preview quando gira in background (la superficie grafica costa);
 * - risoluzione di analisi piccola, scelta dal [PerformanceMode];
 * - cadenza limitata via software: in attesa bastano 6-8 fotogrammi al secondo;
 * - `unbind()` chiude davvero la fotocamera, non la mette solo in pausa.
 */
class CameraController(context: Context) {

    private val appContext = context.applicationContext
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var executor: ExecutorService? = null

    private val minIntervalMs = AtomicInteger(66)
    private var lastFrameAt = 0L
    private var reusableMatrix = Matrix()

    var isBound: Boolean = false
        private set

    /**
     * Accende la fotocamera e comincia ad analizzare.
     *
     * @param preview opzionale: usato solo dalla schermata di calibrazione.
     * @param onFrame chiamato su un thread di lavoro con un bitmap gia' ruotato
     *   in verticale. Il chiamante non deve conservarne il riferimento.
     */
    fun bind(
        lifecycleOwner: LifecycleOwner,
        mode: PerformanceMode,
        targetFps: Int,
        preview: Preview? = null,
        onError: (Throwable) -> Unit = {},
        onFrame: (Bitmap, Long) -> Unit,
    ) {
        setTargetFps(targetFps)
        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener({
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                val workExecutor = executor ?: Executors.newSingleThreadExecutor().also { executor = it }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(mode.analysisWidth, mode.analysisHeight))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()

                imageAnalysis.setAnalyzer(workExecutor) { proxy ->
                    dispatch(proxy, onFrame)
                }

                cameraProvider.unbindAll()
                val useCases = buildList<UseCase> {
                    add(imageAnalysis)
                    preview?.let { add(it) }
                }.toTypedArray()

                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    *useCases,
                )
                analysis = imageAnalysis
                isBound = true
            } catch (t: Throwable) {
                Log.e(TAG, "Impossibile aprire la fotocamera", t)
                isBound = false
                onError(t)
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    /** Cambia la cadenza senza ricreare la sessione: e' il caso giallo -> verde. */
    fun setTargetFps(fps: Int) {
        val safeFps = fps.coerceIn(2, 60)
        minIntervalMs.set(1000 / safeFps)
    }

    fun unbind() {
        runCatching {
            analysis?.clearAnalyzer()
            provider?.unbindAll()
        }
        analysis = null
        isBound = false
        lastFrameAt = 0L
    }

    /** Da chiamare quando il servizio muore: chiude anche il thread di lavoro. */
    fun release() {
        unbind()
        executor?.shutdown()
        executor = null
        provider = null
    }

    private fun dispatch(proxy: ImageProxy, onFrame: (Bitmap, Long) -> Unit) {
        try {
            val now = SystemClock.uptimeMillis()
            if (now - lastFrameAt < minIntervalMs.get()) return
            lastFrameAt = now

            val source = proxy.toBitmap()
            val rotation = proxy.imageInfo.rotationDegrees
            val upright = if (rotation == 0) {
                source
            } else {
                reusableMatrix.reset()
                reusableMatrix.postRotate(rotation.toFloat())
                Bitmap.createBitmap(source, 0, 0, source.width, source.height, reusableMatrix, false)
            }
            onFrame(upright, now)
        } catch (t: Throwable) {
            Log.w(TAG, "Fotogramma scartato", t)
        } finally {
            proxy.close()
        }
    }

    private companion object {
        const val TAG = "AirScroll/Camera"
    }
}
