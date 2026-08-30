package dev.airscroll.app.ui.calibration

import android.app.Application
import androidx.camera.core.Preview
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import dev.airscroll.app.bootstrap.ServiceLocator
import dev.airscroll.app.util.visionFailureHeadline
import dev.airscroll.core.camera.CameraController
import dev.airscroll.core.camera.DeviceCapabilities
import dev.airscroll.core.common.model.HandFrame
import dev.airscroll.core.gesture.ReachMap
import dev.airscroll.core.settings.CalibrationProfile
import dev.airscroll.core.vision.MediaPipeHandTracker
import dev.airscroll.core.vision.VisionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/** I passi della calibrazione, nell'ordine in cui vengono presentati. */
enum class CalibrationStep {
    INTRO,

    /**
     * Mano ferma al centro.
     *
     * Misura tre cose in una volta: quanto trema la mano (da cui esce la zona
     * neutra), quanto e' grande vista dalla fotocamera (da cui esce il
     * guadagno automatico per la distanza) e dove sta il punto di riposo, che
     * diventa il centro del cerchio.
     */
    CENTER,

    /** Il cerchio da completare, muovendo la mano in tutte le direzioni. */
    RING,

    DONE,
}

data class CalibrationUiState(
    val step: CalibrationStep = CalibrationStep.INTRO,
    val recording: Boolean = false,
    val progress: Float = 0f,
    val handVisible: Boolean = false,
    val error: String? = null,
    /** Diagnosi tecnica dell'avvio fallito, da mostrare e da poter copiare. */
    val diagnostics: String? = null,
    val result: CalibrationProfile = CalibrationProfile.Default,

    /** Quali spicchi del cerchio sono gia' accesi. */
    val sectors: List<Boolean> = List(ReachMap.DEFAULT_SECTORS) { false },
    /** Posizione della mano dentro il cerchio, con 1 = raggio da raggiungere. */
    val handOffsetX: Float = 0f,
    val handOffsetY: Float = 0f,
    /** Quanti spicchi mancano, per dirlo a parole. */
    val sectorsLeft: Int = ReachMap.DEFAULT_SECTORS,
    /**
     * true quando si puo' concludere anche senza cerchio pieno.
     *
     * Chi ha poca mobilita' non deve restare bloccato davanti a un cerchio che
     * non si chiude: una calibrazione parziale, e dichiarata tale, vale piu' di
     * una calibrazione impossibile da finire.
     */
    val canFinishEarly: Boolean = false,
)

/**
 * Calibrazione a cerchio, ispirata a Face ID.
 *
 * Invece di far indovinare all'utente dei numeri astratti, gli si chiede di
 * fare il movimento vero e si misura. Il cerchio non e' decorazione: e' cio'
 * che lo spinge a esplorare **tutte** le direzioni. La versione precedente
 * chiedeva "muovi su e giu'" e "muovi a destra e sinistra", e da quelle ricavava
 * due numeri soli - un'ampiezza verticale e una orizzontale - come se il
 * braccio salisse e scendesse allo stesso modo. Non e' cosi' per nessuno, e
 * quella media e' meta' del motivo per cui alla prova uno dei due sensi dello
 * scorrimento sembrava non funzionare.
 *
 * Ora le portate misurate sono quattro, una per verso.
 */
class CalibrationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ServiceLocator.settings(application)
    private val camera = CameraController(application)
    private val performanceMode = DeviceCapabilities.suggestPerformanceMode(application)
    private val tracker = MediaPipeHandTracker(
        context = application,
        config = VisionConfig.Default.copy(preferGpu = performanceMode.preferGpu),
    )

    private val _state = MutableStateFlow(CalibrationUiState())
    val state: StateFlow<CalibrationUiState> = _state.asStateFlow()

    private val reachMap = ReachMap()
    private val spanSamples = ArrayList<Float>(128)
    private val xSamples = ArrayList<Float>(128)
    private val ySamples = ArrayList<Float>(128)

    private var recordingStartedAt = NOT_RECORDING
    private var lastHandSeenAt = 0L
    private var partial = CalibrationProfile.Default

    init {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            tracker.frames.collect(::onFrame)
        }
    }

    fun startCamera(lifecycleOwner: LifecycleOwner, preview: Preview) {
        tracker.start()
        if (!tracker.isReady) {
            // Si segnala l'errore ma si accende comunque la fotocamera: vedere
            // l'anteprima viva dice all'utente che il guasto non e' la sua
            // fotocamera. Uscire qui lasciava un rettangolo nero e nessun indizio.
            val failure = tracker.failure
            _state.value = _state.value.copy(
                error = visionFailureHeadline(getApplication<Application>(), failure),
                diagnostics = failure?.report(),
            )
        }
        camera.bind(
            lifecycleOwner = lifecycleOwner,
            mode = performanceMode,
            targetFps = CALIBRATION_FPS,
            preview = preview,
            onError = { error -> _state.value = _state.value.copy(error = error.message) },
            onFrame = { bitmap, timestamp -> tracker.submit(bitmap, timestamp) },
        )
    }

    fun stopCamera() {
        camera.unbind()
        tracker.stop()
    }

    override fun onCleared() {
        camera.release()
        tracker.stop()
        super.onCleared()
    }

    fun beginNextStep() {
        val next = when (_state.value.step) {
            CalibrationStep.INTRO -> CalibrationStep.CENTER
            CalibrationStep.CENTER -> CalibrationStep.RING
            CalibrationStep.RING -> CalibrationStep.DONE
            CalibrationStep.DONE -> CalibrationStep.DONE
        }
        clearSamples()
        recordingStartedAt = NOT_RECORDING
        if (next == CalibrationStep.DONE) finishRing()
        _state.value = _state.value.copy(
            step = next,
            recording = next == CalibrationStep.CENTER || next == CalibrationStep.RING,
            progress = 0f,
            error = null,
        )
    }

    fun restart() {
        clearSamples()
        reachMap.reset()
        partial = CalibrationProfile.Default
        recordingStartedAt = NOT_RECORDING
        _state.value = CalibrationUiState()
    }

    fun save(onSaved: () -> Unit) {
        val profile = partial
            .withDerivedRanges()
            .copy(completed = true, calibratedAtMillis = System.currentTimeMillis())
        viewModelScope.launch {
            repository.saveCalibration(profile)
            onSaved()
        }
    }

    /** Salta la calibrazione fine e tiene i valori di partenza. */
    fun useDefaults(onSaved: () -> Unit) {
        viewModelScope.launch {
            repository.saveCalibration(
                CalibrationProfile.Default.copy(
                    completed = true,
                    calibratedAtMillis = System.currentTimeMillis(),
                )
            )
            onSaved()
        }
    }

    private fun onFrame(frame: HandFrame) {
        val current = _state.value
        val now = frame.timestampMs

        if (frame.present) lastHandSeenAt = now
        val visible = frame.present || (now - lastHandSeenAt) < HAND_GRACE_MS
        if (current.handVisible != visible) {
            _state.value = _state.value.copy(handVisible = visible)
        }
        if (!current.recording) return

        if (!frame.present) {
            // Senza mano il cronometro si ferma: cosi' l'utente non deve
            // rincorrere una barra che avanza da sola.
            if (now - lastHandSeenAt > HAND_GRACE_MS) recordingStartedAt = NOT_RECORDING
            return
        }

        if (recordingStartedAt == NOT_RECORDING) {
            recordingStartedAt = now
            if (current.step == CalibrationStep.CENTER) clearSamples()
        }

        when (current.step) {
            CalibrationStep.CENTER -> onCenterFrame(frame, now)
            CalibrationStep.RING -> onRingFrame(frame, now)
            else -> Unit
        }
    }

    private fun onCenterFrame(frame: HandFrame, now: Long) {
        spanSamples.add(frame.handSpan)
        xSamples.add(frame.palmX)
        ySamples.add(frame.palmY)

        val progress = ((now - recordingStartedAt).toFloat() / CENTER_DURATION_MS).coerceIn(0f, 1f)
        _state.value = _state.value.copy(progress = progress)

        if (progress < 1f || spanSamples.size < MIN_SAMPLES) return

        partial = partial.copy(
            referenceHandSpan = median(spanSamples).coerceIn(MIN_HAND_SPAN, MAX_HAND_SPAN),
            tremor = (maxOf(deviation(xSamples), deviation(ySamples)) * TREMOR_SAFETY)
                .coerceIn(MIN_TREMOR, MAX_TREMOR),
        )
        reachMap.centerOn(median(xSamples), median(ySamples))
        _state.value = _state.value.copy(recording = false, progress = 1f, result = partial)
    }

    private fun onRingFrame(frame: HandFrame, now: Long) {
        val position = reachMap.accept(frame.palmX, frame.palmY)
        val elapsed = now - recordingStartedAt

        _state.value = _state.value.copy(
            progress = reachMap.progress,
            sectors = reachMap.sectorStates(),
            sectorsLeft = ReachMap.DEFAULT_SECTORS - reachMap.filledCount,
            handOffsetX = (position.dx / ReachMap.TARGET_RADIUS).coerceIn(-DOT_LIMIT, DOT_LIMIT),
            handOffsetY = (position.dy / ReachMap.TARGET_RADIUS).coerceIn(-DOT_LIMIT, DOT_LIMIT),
            canFinishEarly = elapsed > EARLY_FINISH_AFTER_MS &&
                reachMap.filledCount >= MIN_SECTORS_TO_ACCEPT,
        )

        if (reachMap.isComplete) {
            finishRing()
            _state.value = _state.value.copy(
                step = CalibrationStep.DONE,
                recording = false,
                progress = 1f,
                result = partial,
            )
        }
    }

    /**
     * Trasforma il cerchio in quattro portate.
     *
     * Gli spicchi rimasti spenti non diventano zero: si ripiega sul valore di
     * partenza. Una portata a zero renderebbe quel verso violentemente
     * nervoso, e un cerchio lasciato a meta' e' proprio il caso in cui non
     * sappiamo abbastanza per essere aggressivi.
     */
    private fun finishRing() {
        val measured = reachMap.toReach()
        partial = partial.copy(
            reachUp = clampReach(measured.up, CalibrationProfile.DEFAULT_VERTICAL_RANGE),
            reachDown = clampReach(measured.down, CalibrationProfile.DEFAULT_VERTICAL_RANGE),
            reachLeft = clampReach(measured.left, CalibrationProfile.DEFAULT_HORIZONTAL_RANGE),
            reachRight = clampReach(measured.right, CalibrationProfile.DEFAULT_HORIZONTAL_RANGE),
        ).withDerivedRanges()
    }

    private fun clampReach(measured: Float, fallback: Float): Float =
        if (measured < MIN_RANGE) fallback else measured.coerceAtMost(MAX_RANGE)

    private fun clearSamples() {
        spanSamples.clear()
        xSamples.clear()
        ySamples.clear()
    }

    private fun median(values: List<Float>): Float {
        if (values.isEmpty()) return CalibrationProfile.DEFAULT_HAND_SPAN
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    private fun deviation(values: List<Float>): Float {
        if (values.size < 2) return CalibrationProfile.DEFAULT_TREMOR
        val mean = values.average().toFloat()
        val variance = values.sumOf { value ->
            val diff = (value - mean).toDouble()
            diff * diff
        } / values.size
        return sqrt(variance).toFloat()
    }

    private companion object {
        const val NOT_RECORDING = -1L
        const val CALIBRATION_FPS = 20
        const val HAND_GRACE_MS = 600L
        const val CENTER_DURATION_MS = 2_500f
        const val MIN_SAMPLES = 10
        const val TREMOR_SAFETY = 2.2f
        const val MIN_TREMOR = 0.006f
        const val MAX_TREMOR = 0.05f
        const val MIN_RANGE = 0.06f
        const val MAX_RANGE = 0.45f
        const val MIN_HAND_SPAN = 0.04f
        const val MAX_HAND_SPAN = 0.45f

        /** Il puntino puo' uscire un po' dal cerchio, ma non sparire. */
        const val DOT_LIMIT = 1.6f

        /** Dopo quanto si propone di concludere con il cerchio incompleto. */
        const val EARLY_FINISH_AFTER_MS = 25_000L
        const val MIN_SECTORS_TO_ACCEPT = 6
    }
}
