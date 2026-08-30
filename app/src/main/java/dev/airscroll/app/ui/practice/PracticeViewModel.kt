package dev.airscroll.app.ui.practice

import android.app.Application
import androidx.camera.core.Preview
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import dev.airscroll.app.R
import dev.airscroll.app.util.visionFailureHeadline
import dev.airscroll.app.bootstrap.ServiceLocator
import dev.airscroll.apps.api.AppCategory
import dev.airscroll.apps.api.AppProfile
import dev.airscroll.core.camera.CameraController
import dev.airscroll.core.camera.DeviceCapabilities
import dev.airscroll.core.common.model.EngineState
import dev.airscroll.core.common.model.EngineStatus
import dev.airscroll.core.common.model.HandFrame
import dev.airscroll.core.common.model.HandSignal
import dev.airscroll.core.common.model.ScrollCommand
import dev.airscroll.core.common.model.VolumeCommand
import dev.airscroll.core.gesture.GestureEngine
import dev.airscroll.core.settings.AirScrollSettings
import dev.airscroll.core.settings.effective
import dev.airscroll.core.vision.MediaPipeHandTracker
import dev.airscroll.core.vision.TrackerStats
import dev.airscroll.core.vision.VisionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PracticeUiState(
    val engineState: EngineState = EngineState.IDLE,
    val handPresent: Boolean = false,
    val signal: HandSignal = HandSignal.NONE,
    val gain: Float = 1f,
    val velocityPxPerSec: Float = 0f,
    val volumeSteps: Int = 0,
    val error: String? = null,
    /** Diagnosi tecnica dell'avvio fallito, da mostrare e da poter copiare. */
    val diagnostics: String? = null,
    val usingGpu: Boolean = false,
    val stats: TrackerStats = TrackerStats(),
)

/**
 * La palestra dei gesti.
 *
 * Usa **lo stesso motore** del servizio vero: stessa macchina a stati, stesso
 * filtro, stessa mappatura. L'unica differenza e' dove finisce la velocita': qui
 * scorre un contenuto finto dentro l'app, invece di diventare un gesto di
 * accessibilita' su un'altra app.
 *
 * Serve a provare i gesti e tarare i cursori *prima* di combattere con i
 * permessi di Android, e quello che si regola qui vale anche fuori.
 */
class PracticeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ServiceLocator.settings(application)
    private val camera = CameraController(application)
    private val performanceMode = DeviceCapabilities.suggestPerformanceMode(application)

    private var tracker = MediaPipeHandTracker(
        context = application,
        config = VisionConfig.Default.copy(preferGpu = performanceMode.preferGpu),
    )

    private val _state = MutableStateFlow(PracticeUiState())
    val state: StateFlow<PracticeUiState> = _state.asStateFlow()

    /** Velocita' corrente in pixel al secondo. Positiva = il dito scende. */
    private val _velocity = MutableStateFlow(0f)
    val velocity: StateFlow<Float> = _velocity.asStateFlow()

    private val _settings = MutableStateFlow(AirScrollSettings.Default)
    val settings: StateFlow<AirScrollSettings> = _settings.asStateFlow()

    private val engine = GestureEngine(PracticeListener())
    private var framesJob: Job? = null
    private var running = false

    init {
        viewModelScope.launch {
            repository.settings.collect { updated ->
                _settings.value = updated
                // In palestra la finestra di attesa non scade: la fotocamera resta
                // accesa finche' la schermata e' aperta, altrimenti si perderebbe
                // il gesto mentre si legge la spiegazione.
                engine.updateSettings(
                    updated.effective.copy(
                        serviceEnabled = true,
                        waitingWindowMs = PRACTICE_WINDOW_MS,
                    )
                )
                if (running) arm()
            }
        }
        observeFrames()
        viewModelScope.launch {
            while (isActive) {
                delay(TICK_MS)
                engine.tick()
                if (running) {
                    // I contatori del tracker sono l'unico modo di distinguere
                    // "non vedo la mano" da "il modello non sta girando".
                    _state.value = _state.value.copy(stats = tracker.stats())
                }
            }
        }
    }

    fun start(lifecycleOwner: LifecycleOwner, preview: Preview) {
        running = true
        tracker.start()
        // Anche col riconoscitore morto la fotocamera si accende lo stesso:
        // l'anteprima viva e' l'unica prova che il guasto sta altrove.
        val failure = tracker.failure
        _state.value = _state.value.copy(
            usingGpu = tracker.usingGpu,
            error = if (tracker.isReady) null else visionFailureHeadline(getApplication<Application>(), failure),
            diagnostics = failure?.report(),
        )
        camera.bind(
            lifecycleOwner = lifecycleOwner,
            mode = performanceMode,
            targetFps = performanceMode.activeFps,
            preview = preview,
            onError = { error -> _state.value = _state.value.copy(error = error.message) },
            onFrame = { bitmap, timestamp -> tracker.submit(bitmap, timestamp) },
        )
        arm()
    }

    fun stop() {
        running = false
        camera.unbind()
        tracker.stop()
        _velocity.value = 0f
    }

    /** Rimette il motore in attesa del pollice in su. */
    fun arm() {
        engine.onForegroundApp(PRACTICE_PACKAGE, PRACTICE_PROFILE)
        engine.rearm()
    }

    fun setSensitivity(value: Float) = viewModelScope.launch { repository.setSensitivity(value) }
    fun setNeutralZone(value: Float) = viewModelScope.launch { repository.setNeutralZoneScale(value) }
    fun setKitchenMode(value: Boolean) = viewModelScope.launch { repository.setKitchenMode(value) }

    override fun onCleared() {
        camera.release()
        tracker.stop()
        super.onCleared()
    }

    private fun observeFrames() {
        framesJob?.cancel()
        val current = tracker
        framesJob = viewModelScope.launch(Dispatchers.Main.immediate) {
            current.frames.collect { frame -> onFrame(frame) }
        }
    }

    private fun onFrame(frame: HandFrame) {
        engine.onFrame(frame)
        _state.value = _state.value.copy(
            handPresent = frame.present,
            signal = frame.signal,
        )
    }

    private inner class PracticeListener : GestureEngine.Listener {
        override fun onStatus(status: EngineStatus) {
            _state.value = _state.value.copy(
                engineState = status.state,
                gain = status.effectiveGain,
            )
            if (status.state != EngineState.ACTIVE) _velocity.value = 0f
        }

        override fun onScroll(command: ScrollCommand) {
            _velocity.value = command.velocityPxPerSec
            _state.value = _state.value.copy(velocityPxPerSec = command.velocityPxPerSec)
        }

        override fun onVolume(command: VolumeCommand) {
            // In palestra il volume non si tocca davvero: si mostra soltanto, cosi'
            // si capisce quanto si sta spingendo senza assordare nessuno.
            _state.value = _state.value.copy(
                volumeSteps = _state.value.volumeSteps + command.steps,
            )
        }

        override fun onCameraNeed(needed: Boolean, targetFps: Int) {
            if (running && camera.isBound) camera.setTargetFps(targetFps)
        }

        override fun onHaptic() = Unit
    }

    private companion object {
        const val TICK_MS = 100L
        const val PRACTICE_WINDOW_MS = 30 * 60 * 1000L
        const val PRACTICE_PACKAGE = "dev.airscroll.practice"

        val PRACTICE_PROFILE = AppProfile(
            id = "practice",
            displayName = "Palestra",
            packageNames = setOf(PRACTICE_PACKAGE),
            category = AppCategory.READER,
        )
    }
}
