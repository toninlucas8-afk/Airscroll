package dev.airscroll.app.ui.lab

import android.app.Application
import androidx.annotation.StringRes
import androidx.camera.core.Preview
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import dev.airscroll.app.R
import dev.airscroll.app.util.RecordingWriter
import dev.airscroll.core.camera.CameraController
import dev.airscroll.core.camera.DeviceCapabilities
import dev.airscroll.core.common.model.HandFrame
import dev.airscroll.core.common.model.HandSignal
import dev.airscroll.core.vision.MediaPipeHandTracker
import dev.airscroll.core.vision.TrackerStats
import dev.airscroll.core.vision.VisionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Le prese da registrare, in ordine.
 *
 * Ci sono anche due prese "negative" - mano aperta e mano a riposo - perche'
 * per tarare un riconoscitore serve sapere sia quando deve dire di si' sia
 * quando deve dire di no. Con soli esempi positivi si finisce per abbassare le
 * soglie finche' tutto attiva tutto.
 */
enum class LabTake(
    val expected: String,
    @StringRes val prompt: Int,
    @StringRes val hint: Int,
    val durationMs: Long,
) {
    THUMB_UP("THUMB_UP", R.string.lab_take_thumb_up, R.string.lab_take_thumb_up_hint, 5_000L),
    FIST("CLOSED_FIST", R.string.lab_take_fist, R.string.lab_take_fist_hint, 5_000L),
    OPEN_PALM("OPEN_PALM", R.string.lab_take_palm, R.string.lab_take_palm_hint, 4_000L),
    MOTION("MOTION", R.string.lab_take_motion, R.string.lab_take_motion_hint, 6_000L),
    IDLE("NONE", R.string.lab_take_idle, R.string.lab_take_idle_hint, 4_000L),
}

data class LabUiState(
    val takeIndex: Int = 0,
    val recording: Boolean = false,
    val progress: Float = 0f,
    val framesInTake: Int = 0,
    val handPresent: Boolean = false,
    val signal: HandSignal = HandSignal.NONE,
    val confidence: Float = 0f,
    val stats: TrackerStats = TrackerStats(),
    val finished: Boolean = false,
    val savedFile: File? = null,
    val error: String? = null,
) {
    val take: LabTake get() = LabTake.entries[takeIndex.coerceIn(0, LabTake.entries.lastIndex)]
    val isLastTake: Boolean get() = takeIndex >= LabTake.entries.lastIndex
}

/**
 * Il laboratorio: registra come *questa* mano appare al modello.
 *
 * Non esce nessuna immagine dal telefono. Vengono salvate solo le coordinate
 * dei 21 punti, il gesto riconosciuto e il suo punteggio, in un file CSV che
 * l'utente puo' aprire e leggere prima di decidere se condividerlo.
 */
class LabViewModel(application: Application) : AndroidViewModel(application) {

    private val camera = CameraController(application)
    private val performanceMode = DeviceCapabilities.suggestPerformanceMode(application)
    private val tracker = MediaPipeHandTracker(
        context = application,
        config = VisionConfig.Default.copy(
            preferGpu = performanceMode.preferGpu,
            // La soglia piu' bassa possibile: in laboratorio vogliamo vedere
            // anche i riconoscimenti incerti, sono quelli che spiegano i guai.
            minGestureConfidence = 0f,
            includeLandmarks = true,
        ),
    )

    private val _state = MutableStateFlow(LabUiState())
    val state: StateFlow<LabUiState> = _state.asStateFlow()

    private val takes = LabTake.entries.map { RecordingWriter.Take(it.expected) }
    private var recordingStartedAt = NOT_RECORDING

    init {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            tracker.frames.collect(::onFrame)
        }
    }

    fun start(lifecycleOwner: LifecycleOwner, preview: Preview) {
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
            targetFps = performanceMode.activeFps,
            preview = preview,
            onError = { error -> _state.value = _state.value.copy(error = error.message) },
            onFrame = { bitmap, timestamp -> tracker.submit(bitmap, timestamp) },
        )
    }

    fun stop() {
        camera.unbind()
        tracker.stop()
    }

    override fun onCleared() {
        camera.release()
        tracker.stop()
        super.onCleared()
    }

    fun startTake() {
        recordingStartedAt = NOT_RECORDING
        takes[_state.value.takeIndex].frames.clear()
        _state.value = _state.value.copy(recording = true, progress = 0f, framesInTake = 0)
    }

    fun redoTake() {
        _state.value = _state.value.copy(recording = false, progress = 0f, framesInTake = 0)
        startTake()
    }

    private fun onFrame(frame: HandFrame) {
        val current = _state.value
        _state.value = current.copy(
            handPresent = frame.present,
            signal = frame.signal,
            confidence = frame.signalConfidence,
            stats = tracker.stats(),
        )
        if (!current.recording) return

        if (recordingStartedAt == NOT_RECORDING) recordingStartedAt = frame.timestampMs
        takes[current.takeIndex].frames.add(frame)

        val elapsed = frame.timestampMs - recordingStartedAt
        val duration = current.take.durationMs
        val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
        _state.value = _state.value.copy(
            progress = progress,
            framesInTake = takes[current.takeIndex].frames.size,
        )

        if (elapsed >= duration) finishTake()
    }

    private fun finishTake() {
        recordingStartedAt = NOT_RECORDING
        val current = _state.value
        if (current.isLastTake) {
            _state.value = current.copy(recording = false, progress = 1f)
            save()
        } else {
            _state.value = current.copy(
                recording = false,
                progress = 0f,
                takeIndex = current.takeIndex + 1,
                framesInTake = 0,
            )
        }
    }

    private fun save() {
        viewModelScope.launch(Dispatchers.Default) {
            val csv = RecordingWriter.buildCsv(
                takes = takes,
                stats = tracker.stats(),
                analysisWidth = performanceMode.analysisWidth,
                analysisHeight = performanceMode.analysisHeight,
            )
            val result = runCatching { RecordingWriter.write(getApplication(), csv) }
            _state.value = _state.value.copy(
                finished = true,
                savedFile = result.getOrNull(),
                error = result.exceptionOrNull()?.message,
            )
        }
    }

    fun restartAll() {
        takes.forEach { it.frames.clear() }
        recordingStartedAt = NOT_RECORDING
        _state.value = LabUiState(stats = tracker.stats())
    }

    private companion object {
        const val NOT_RECORDING = -1L
    }
}
