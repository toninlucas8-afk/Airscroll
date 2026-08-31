package dev.airscroll.app.ui.calibration

import android.app.Application
import androidx.camera.core.Preview
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import dev.airscroll.app.BuildConfig
import dev.airscroll.app.bootstrap.ServiceLocator
import dev.airscroll.app.util.visionFailureHeadline
import dev.airscroll.core.camera.CameraController
import dev.airscroll.core.camera.DeviceCapabilities
import dev.airscroll.core.common.model.HandFrame
import dev.airscroll.core.gesture.Aspect
import dev.airscroll.core.gesture.CalibrationMachine
import dev.airscroll.core.settings.CalibrationProfile
import dev.airscroll.core.vision.MediaPipeHandTracker
import dev.airscroll.core.vision.VisionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** I passi della calibrazione. Vivono nel motore, che e' dove sono provati. */
typealias CalibrationStep = CalibrationMachine.Step

/** Quale dei due gesti si sta provando. */
typealias GestureStage = CalibrationMachine.GestureStage

/**
 * Quello che la schermata deve mostrare.
 *
 * E' lo stato della macchina piu' le due cose che solo Android conosce:
 * l'errore di avvio del riconoscitore e la sua diagnosi tecnica.
 */
data class CalibrationUiState(
    val machine: CalibrationMachine.State = CalibrationMachine.State(),
    val error: String? = null,
    /** Diagnosi tecnica dell'avvio fallito, da mostrare e da poter copiare. */
    val diagnostics: String? = null,
) {
    val step get() = machine.step
    val recording get() = machine.recording
    val progress get() = machine.progress
    val handVisible get() = machine.handVisible
    val framing get() = machine.framing
    val framingStuck get() = machine.framingStuck
    val sectors get() = machine.sectors
    val sectorsLeft get() = machine.sectorsLeft
    val handOffsetX get() = machine.handOffsetX
    val handOffsetY get() = machine.handOffsetY
    val canFinishEarly get() = machine.canFinishEarly
    val gestureStage get() = machine.gestureStage
    val gestureStuck get() = machine.gestureStuck
    val report get() = machine.report
    val result get() = machine.profile
}

/**
 * Calibrazione a cerchio, ispirata a Face ID.
 *
 * Invece di far indovinare all'utente dei numeri astratti, gli si chiede di
 * fare il movimento vero e si misura. Le portate misurate sono quattro, una per
 * verso, perche' nessun braccio arriva alla stessa distanza in tutte le
 * direzioni - e forzare una media rende un verso lento e l'altro nervoso.
 *
 * **Qui dentro non c'e' piu' nessuna decisione.** Tutta la macchina a stati -
 * i cinque passi, i casi limite, la pagella - sta in
 * [CalibrationMachine], dove non dipende da Android e dove un test puo' far
 * girare una calibrazione intera in un millisecondo, compresi i casi che a mano
 * non si riproducono mai: il gesto che non arriva, l'inquadratura che non si
 * stabilizza, la mano che scivola durante la misura.
 *
 * Questo file fa il resto: accende la fotocamera, porta i fotogrammi alla
 * macchina, e i suoi stati allo schermo.
 */
class CalibrationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ServiceLocator.settings(application)
    private val camera = CameraController(application)
    private val performanceMode = DeviceCapabilities.suggestPerformanceMode(application)
    private val tracker = MediaPipeHandTracker(
        context = application,
        config = VisionConfig.Default.copy(preferGpu = performanceMode.preferGpu),
    )

    private val machine = CalibrationMachine()

    private val _state = MutableStateFlow(CalibrationUiState())
    val state: StateFlow<CalibrationUiState> = _state.asStateFlow()

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
        machine.advance()
        publish()
    }

    fun redo(aspect: Aspect) {
        machine.redo(aspect)
        publish()
    }

    fun skipGesture() {
        machine.skipGesture()
        publish()
    }

    fun restart() {
        machine.restart()
        _state.value = CalibrationUiState(machine = machine.state)
    }

    fun save(onSaved: () -> Unit) {
        val profile = machine.state.profile
            .withDerivedRanges()
            .copy(
                completed = true,
                calibratedAtMillis = System.currentTimeMillis(),
                // Firmata con la versione che l'ha misurata: e' quello che
                // permette a CalibrationVersionGate di sapere, al prossimo
                // avvio, se questi numeri parlano ancora la stessa lingua del
                // motore che li usa.
                calibratedVersion = BuildConfig.VERSION_NAME,
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
                    calibratedVersion = BuildConfig.VERSION_NAME,
                )
            )
            onSaved()
        }
    }

    private fun onFrame(frame: HandFrame) {
        machine.onFrame(frame, frame.timestampMs)
        publish()
    }

    private fun publish() {
        val corrente = _state.value
        if (corrente.machine != machine.state) {
            _state.value = corrente.copy(machine = machine.state)
        }
    }

    private companion object {
        const val CALIBRATION_FPS = 20
    }
}
