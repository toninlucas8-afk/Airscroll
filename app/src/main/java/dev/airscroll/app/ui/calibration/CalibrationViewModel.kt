package dev.airscroll.app.ui.calibration

import android.app.Application
import androidx.camera.core.Preview
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import dev.airscroll.app.R
import dev.airscroll.app.bootstrap.ServiceLocator
import dev.airscroll.core.camera.CameraController
import dev.airscroll.core.camera.DeviceCapabilities
import dev.airscroll.core.common.model.HandFrame
import dev.airscroll.core.settings.CalibrationProfile
import dev.airscroll.core.vision.MediaPipeHandTracker
import dev.airscroll.core.vision.VisionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

/** I passi della calibrazione, nell'ordine in cui vengono presentati. */
enum class CalibrationStep {
    INTRO,
    DISTANCE,
    STILL,
    VERTICAL,
    HORIZONTAL,
    DONE,
}

data class CalibrationUiState(
    val step: CalibrationStep = CalibrationStep.INTRO,
    val recording: Boolean = false,
    val progress: Float = 0f,
    val handVisible: Boolean = false,
    val error: String? = null,
    val result: CalibrationProfile = CalibrationProfile.Default,
)

/**
 * Calibrazione ispirata a Face ID: invece di far indovinare all'utente dei
 * numeri astratti, gli si chiede di fare i movimenti veri e si misura.
 *
 * Le quattro misure servono a cose diverse:
 * - distanza abituale -> guadagno del profilo automatico;
 * - mano ferma -> ampiezza del tremolio, cioe' la zona neutra;
 * - su e giu' -> quanto ampio e' un movimento "pieno" per questa persona;
 * - destra e sinistra -> la stessa cosa per il volume.
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

    private val spanSamples = ArrayList<Float>(256)
    private val xSamples = ArrayList<Float>(256)
    private val ySamples = ArrayList<Float>(256)

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
            _state.value = _state.value.copy(
                error = getApplication<Application>().getString(R.string.error_vision_unavailable),
            )
            return
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
            CalibrationStep.INTRO -> CalibrationStep.DISTANCE
            CalibrationStep.DISTANCE -> CalibrationStep.STILL
            CalibrationStep.STILL -> CalibrationStep.VERTICAL
            CalibrationStep.VERTICAL -> CalibrationStep.HORIZONTAL
            CalibrationStep.HORIZONTAL -> CalibrationStep.DONE
            CalibrationStep.DONE -> CalibrationStep.DONE
        }
        clearSamples()
        recordingStartedAt = NOT_RECORDING
        _state.value = _state.value.copy(
            step = next,
            recording = next != CalibrationStep.DONE && next != CalibrationStep.INTRO,
            progress = 0f,
            error = null,
        )
    }

    fun restart() {
        clearSamples()
        partial = CalibrationProfile.Default
        recordingStartedAt = NOT_RECORDING
        _state.value = CalibrationUiState()
    }

    fun save(onSaved: () -> Unit) {
        val profile = partial.copy(
            completed = true,
            calibratedAtMillis = System.currentTimeMillis(),
        )
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
            clearSamples()
        }

        spanSamples.add(frame.handSpan)
        xSamples.add(frame.palmX)
        ySamples.add(frame.palmY)

        val duration = durationFor(current.step)
        val progress = ((now - recordingStartedAt).toFloat() / duration).coerceIn(0f, 1f)
        _state.value = _state.value.copy(progress = progress)

        if (progress >= 1f && spanSamples.size >= MIN_SAMPLES) {
            finishStep(current.step)
        }
    }

    private fun finishStep(step: CalibrationStep) {
        partial = when (step) {
            CalibrationStep.DISTANCE -> partial.copy(
                referenceHandSpan = median(spanSamples)
                    .coerceIn(MIN_HAND_SPAN, MAX_HAND_SPAN)
            )

            CalibrationStep.STILL -> partial.copy(
                tremor = (maxOf(deviation(xSamples), deviation(ySamples)) * TREMOR_SAFETY)
                    .coerceIn(MIN_TREMOR, MAX_TREMOR)
            )

            CalibrationStep.VERTICAL -> partial.copy(
                verticalRange = halfSpread(ySamples).coerceIn(MIN_RANGE, MAX_RANGE)
            )

            CalibrationStep.HORIZONTAL -> partial.copy(
                horizontalRange = halfSpread(xSamples).coerceIn(MIN_RANGE, MAX_RANGE)
            )

            else -> partial
        }
        _state.value = _state.value.copy(recording = false, progress = 1f, result = partial)
    }

    private fun clearSamples() {
        spanSamples.clear()
        xSamples.clear()
        ySamples.clear()
    }

    private fun durationFor(step: CalibrationStep): Long = when (step) {
        CalibrationStep.DISTANCE -> 2_500L
        CalibrationStep.STILL -> 2_500L
        CalibrationStep.VERTICAL -> 5_000L
        CalibrationStep.HORIZONTAL -> 5_000L
        else -> 1L
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

    /**
     * Semi-ampiezza robusta: percentili invece di min/max, cosi' un singolo
     * fotogramma sbagliato non falsa tutta la calibrazione.
     */
    private fun halfSpread(values: List<Float>): Float {
        if (values.size < MIN_SAMPLES) return CalibrationProfile.DEFAULT_VERTICAL_RANGE
        val sorted = values.sorted()
        val low = sorted[(sorted.size * 0.05f).toInt().coerceIn(0, sorted.lastIndex)]
        val high = sorted[(sorted.size * 0.95f).toInt().coerceIn(0, sorted.lastIndex)]
        return abs(high - low) / 2f
    }

    private companion object {
        const val NOT_RECORDING = -1L
        const val CALIBRATION_FPS = 20
        const val HAND_GRACE_MS = 600L
        const val MIN_SAMPLES = 10
        const val TREMOR_SAFETY = 2.2f
        const val MIN_TREMOR = 0.006f
        const val MAX_TREMOR = 0.05f
        const val MIN_RANGE = 0.08f
        const val MAX_RANGE = 0.45f
        const val MIN_HAND_SPAN = 0.04f
        const val MAX_HAND_SPAN = 0.45f
    }
}
