package dev.airscroll.app.ui.power

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import dev.airscroll.core.camera.CameraController
import dev.airscroll.core.camera.DeviceCapabilities
import dev.airscroll.core.power.BatteryProbe
import dev.airscroll.core.power.PowerReport
import dev.airscroll.core.power.PowerSample
import dev.airscroll.core.power.averageDrawMicroAmps
import dev.airscroll.core.vision.MediaPipeHandTracker
import dev.airscroll.core.vision.VisionConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Le tre fasi della misura.
 *
 * Il consumo assoluto non direbbe quasi niente: lo schermo acceso pesa piu' di
 * tutto il resto messo insieme. Quello che serve e' il **confronto** fra il
 * telefono che non fa niente e il telefono che sta usando AirScroll, misurati
 * a pochi secondi di distanza e nelle stesse condizioni.
 */
enum class PowerPhase {
    INTRO,

    /** Telefono acceso, AirScroll fermo. E' il metro di paragone. */
    BASELINE,

    /** Fotocamera aperta alla cadenza dell'attesa, come nello stato giallo. */
    WAITING,

    /** Fotocamera e riconoscimento a pieno regime, come nello stato verde. */
    ACTIVE,

    DONE,
}

data class PowerUiState(
    val phase: PowerPhase = PowerPhase.INTRO,
    val progress: Float = 0f,
    val liveMilliAmps: Int = 0,
    val supported: Boolean = true,
    val charging: Boolean = false,
    val report: PowerReport? = null,
    val capacityMilliAmpHours: Int = 0,
    val framesAnalysed: Long = 0L,
    val framesSkipped: Long = 0L,
)

/**
 * Misura quanto costa AirScroll, invece di ragionarci sopra.
 *
 * Ogni fase dura venti secondi. La fotocamera viene accesa solo dove serve, e
 * il riconoscitore riceve fotogrammi solo nella fase attiva: cosi' le tre
 * misure isolano davvero le tre voci - telefono acceso, fotocamera aperta,
 * fotocamera piu' inferenza.
 */
class PowerViewModel(application: Application) : AndroidViewModel(application) {

    private val probe = BatteryProbe(application)
    private val camera = CameraController(application)
    private val performanceMode = DeviceCapabilities.suggestPerformanceMode(application)
    private val tracker = MediaPipeHandTracker(
        context = application,
        config = VisionConfig.Default.copy(preferGpu = performanceMode.preferGpu),
    )

    private val _state = MutableStateFlow(PowerUiState())
    val state: StateFlow<PowerUiState> = _state.asStateFlow()

    private var run: Job? = null
    private var baseline = 0L
    private var waiting = 0L

    init {
        _state.value = _state.value.copy(
            supported = probe.isSupported,
            charging = probe.isCharging(),
            capacityMilliAmpHours = probe.estimatedCapacityMilliAmpHours(),
        )
    }

    fun start(lifecycleOwner: LifecycleOwner) {
        if (run?.isActive == true) return
        run = viewModelScope.launch {
            baseline = measure(PowerPhase.BASELINE, lifecycleOwner)
            waiting = measure(PowerPhase.WAITING, lifecycleOwner)
            val active = measure(PowerPhase.ACTIVE, lifecycleOwner)
            stopCamera()
            val stats = tracker.stats()
            _state.value = _state.value.copy(
                phase = PowerPhase.DONE,
                progress = 1f,
                report = PowerReport(baseline, waiting, active),
                framesAnalysed = stats.results,
                framesSkipped = stats.droppedBusy,
            )
        }
    }

    fun restart() {
        run?.cancel()
        stopCamera()
        _state.value = PowerUiState(
            supported = probe.isSupported,
            charging = probe.isCharging(),
            capacityMilliAmpHours = probe.estimatedCapacityMilliAmpHours(),
        )
    }

    override fun onCleared() {
        run?.cancel()
        camera.release()
        tracker.stop()
        super.onCleared()
    }

    private suspend fun measure(phase: PowerPhase, lifecycleOwner: LifecycleOwner): Long {
        _state.value = _state.value.copy(phase = phase, progress = 0f)
        prepare(phase, lifecycleOwner)

        // Qualche secondo perche' il telefono si assesti: subito dopo aver
        // acceso la fotocamera sta ancora facendo altro, e quei campioni
        // racconterebbero il transitorio invece del regime.
        delay(SETTLE_MS)

        val samples = ArrayList<PowerSample>(PHASE_SECONDS * SAMPLES_PER_SECOND)
        val total = PHASE_SECONDS * SAMPLES_PER_SECOND
        repeat(total) { index ->
            if (!viewModelScope.isActive) return@repeat
            probe.sample()?.let { samples.add(it) }
            _state.value = _state.value.copy(
                progress = (index + 1f) / total,
                liveMilliAmps = (samples.lastOrNull()?.microAmps?.let { kotlin.math.abs(it) / 1000 }
                    ?: 0L).toInt(),
            )
            delay(1000L / SAMPLES_PER_SECOND)
        }
        return averageDrawMicroAmps(samples)
    }

    private fun prepare(phase: PowerPhase, lifecycleOwner: LifecycleOwner) {
        when (phase) {
            PowerPhase.BASELINE -> stopCamera()

            PowerPhase.WAITING -> {
                // Fotocamera aperta ma nessuna inferenza: e' il costo del solo
                // sensore, che nello stato giallo e' quasi tutto il conto.
                bindCamera(lifecycleOwner, performanceMode.waitingFps, analyse = false)
            }

            PowerPhase.ACTIVE -> {
                tracker.start()
                bindCamera(lifecycleOwner, performanceMode.activeFps, analyse = true)
            }

            else -> Unit
        }
    }

    private fun bindCamera(lifecycleOwner: LifecycleOwner, fps: Int, analyse: Boolean) {
        if (camera.isBound) {
            camera.setTargetFps(fps)
            analysing = analyse
            return
        }
        analysing = analyse
        camera.bind(
            lifecycleOwner = lifecycleOwner,
            mode = performanceMode,
            targetFps = fps,
            preview = null,
            onError = { },
            onFrame = { bitmap, timestamp -> if (analysing) tracker.submit(bitmap, timestamp) },
        )
    }

    @Volatile
    private var analysing = false

    private fun stopCamera() {
        camera.unbind()
        tracker.stop()
        analysing = false
    }

    private companion object {
        const val PHASE_SECONDS = 20
        const val SAMPLES_PER_SECOND = 2
        const val SETTLE_MS = 2_500L
    }
}
