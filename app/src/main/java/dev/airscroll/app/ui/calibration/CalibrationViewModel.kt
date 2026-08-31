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
import dev.airscroll.core.common.model.HandSignal
import dev.airscroll.core.gesture.Aspect
import dev.airscroll.core.gesture.CalibrationReport
import dev.airscroll.core.gesture.FramingHint
import dev.airscroll.core.gesture.ReachMap
import dev.airscroll.core.gesture.framingHint
import dev.airscroll.core.gesture.reportFor
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

    /**
     * Prima di misurare: si controlla di poter misurare.
     *
     * La versione precedente partiva comunque. Se la mano era lontana, mezza
     * fuori inquadratura o al buio, misurava lo stesso e salvava un profilo
     * sbagliato con l'aria di essere a posto - e il difetto si scopriva solo in
     * uso, senza poterlo ricollegare a questi trenta secondi. Un metro storto
     * e' peggio di nessun metro.
     */
    FRAMING,

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

    /**
     * I due comandi, provati sul serio.
     *
     * Qui non si misura niente di nuovo: si verifica che il pollice in su e il
     * pugno chiuso vengano riconosciuti **su questa mano**. Era la verifica che
     * mancava del tutto, e senza la quale si poteva finire una calibrazione
     * impeccabile e scoprire solo in uso che il proprio pollice non veniva mai
     * visto.
     */
    GESTURES,

    /** La pagella: cosa e' stato misurato, quanto vale, cosa conviene rifare. */
    REPORT,
}

/** Quale dei due gesti si sta provando. */
enum class GestureStage { THUMB, FIST, DONE }

data class CalibrationUiState(
    val step: CalibrationStep = CalibrationStep.INTRO,
    val recording: Boolean = false,
    val progress: Float = 0f,
    val handVisible: Boolean = false,
    val error: String? = null,
    /** Diagnosi tecnica dell'avvio fallito, da mostrare e da poter copiare. */
    val diagnostics: String? = null,
    val result: CalibrationProfile = CalibrationProfile.Default,

    /** Cosa non va nell'inquadratura, adesso. */
    val framing: FramingHint = FramingHint.NO_HAND,
    /**
     * true quando l'inquadratura non diventa buona e si insiste da un po'.
     *
     * Serve a non chiudere nessuno fuori: se su questo telefono, con questa
     * luce, le condizioni non si raggiungono mai, la calibrazione deve poter
     * proseguire lo stesso - e sara' la pagella a dire che la distanza non
     * era buona, invece di lasciare l'utente davanti a un passo che non
     * finisce.
     */
    val framingStuck: Boolean = false,

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

    /** La prova dei gesti. */
    val gestureStage: GestureStage = GestureStage.THUMB,
    val thumbRecognised: Boolean = false,
    val fistRecognised: Boolean = false,
    /** true quando il gesto in prova tarda: si offre di andare oltre. */
    val gestureStuck: Boolean = false,

    /** La pagella, disponibile solo all'ultimo passo. */
    val report: CalibrationReport? = null,
)

/**
 * Calibrazione a cerchio, ispirata a Face ID.
 *
 * Invece di far indovinare all'utente dei numeri astratti, gli si chiede di
 * fare il movimento vero e si misura. Il cerchio non e' decorazione: e' cio'
 * che lo spinge a esplorare **tutte** le direzioni, e le portate misurate sono
 * quattro, una per verso - non una media fra due versi che nessun braccio fa
 * allo stesso modo.
 *
 * Dalla 0.6.0 i passi sono sei invece di tre, e i tre nuovi non aggiungono
 * misure: aggiungono **verifiche**. Prima di misurare si controlla di poter
 * misurare; alla fine si prova che i due comandi funzionino davvero; e il
 * risultato viene giudicato invece che solo salvato, cosi' una calibrazione
 * debole si vede subito e si puo' rifare il pezzo che non va - invece di
 * scoprirlo per caso una settimana dopo, dentro un'altra app.
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

    /** Da quando l'inquadratura e' buona senza interruzioni. */
    private var framingOkSince = NOT_RECORDING

    /** Da quando si sta aspettando che l'inquadratura diventi buona. */
    private var framingWaitingSince = NOT_RECORDING

    /** Da quando il gesto in prova viene riconosciuto senza interruzioni. */
    private var gestureHeldSince = NOT_RECORDING

    /** Da quando si sta aspettando il gesto in prova. */
    private var gestureWaitingSince = NOT_RECORDING

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

    /** Avanza al passo successivo. Usato dai pulsanti, non dai fotogrammi. */
    fun beginNextStep() {
        when (_state.value.step) {
            CalibrationStep.INTRO -> enter(CalibrationStep.FRAMING)
            CalibrationStep.FRAMING -> enter(CalibrationStep.CENTER)
            CalibrationStep.CENTER -> enter(CalibrationStep.RING)
            CalibrationStep.RING -> {
                finishRing()
                enter(CalibrationStep.GESTURES)
            }

            CalibrationStep.GESTURES -> enter(CalibrationStep.REPORT)
            CalibrationStep.REPORT -> Unit
        }
    }

    /**
     * Rifa' solo il pezzo debole.
     *
     * E' il motivo per cui esiste la pagella: senza, l'unica scelta davanti a
     * una misura storta sarebbe rifare tutto da capo, che nessuno fa - e quindi
     * la misura storta resta.
     */
    fun redo(aspect: Aspect) {
        when (aspect) {
            Aspect.FRAMING -> enter(CalibrationStep.FRAMING)
            Aspect.STILLNESS -> enter(CalibrationStep.CENTER)
            Aspect.REACH_UP, Aspect.REACH_DOWN, Aspect.REACH_LEFT, Aspect.REACH_RIGHT -> {
                reachMap.reset()
                enter(CalibrationStep.RING)
            }

            Aspect.GESTURES -> enter(CalibrationStep.GESTURES)
        }
    }

    fun restart() {
        clearSamples()
        reachMap.reset()
        partial = CalibrationProfile.Default
        recordingStartedAt = NOT_RECORDING
        framingOkSince = NOT_RECORDING
        framingWaitingSince = NOT_RECORDING
        gestureHeldSince = NOT_RECORDING
        gestureWaitingSince = NOT_RECORDING
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

    // --- macchina a stati ---------------------------------------------------

    private fun enter(step: CalibrationStep) {
        clearSamples()
        recordingStartedAt = NOT_RECORDING
        framingOkSince = NOT_RECORDING
        framingWaitingSince = NOT_RECORDING
        gestureHeldSince = NOT_RECORDING
        gestureWaitingSince = NOT_RECORDING

        val registra = step == CalibrationStep.FRAMING ||
            step == CalibrationStep.CENTER ||
            step == CalibrationStep.RING ||
            step == CalibrationStep.GESTURES

        _state.value = _state.value.copy(
            step = step,
            recording = registra,
            progress = 0f,
            error = null,
            canFinishEarly = false,
            framingStuck = false,
            gestureStuck = false,
            gestureStage = if (step == CalibrationStep.GESTURES) {
                GestureStage.THUMB
            } else {
                _state.value.gestureStage
            },
            report = if (step == CalibrationStep.REPORT) buildReport() else null,
        )
    }

    private fun buildReport(): CalibrationReport {
        val current = _state.value
        return reportFor(
            profile = partial.withDerivedRanges(),
            thumbRecognised = current.thumbRecognised,
            fistRecognised = current.fistRecognised,
        )
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

        // L'inquadratura e la prova dei gesti hanno bisogno anche dei fotogrammi
        // senza mano: e' proprio l'assenza che va raccontata.
        when (current.step) {
            CalibrationStep.FRAMING -> {
                onFramingFrame(frame, now)
                return
            }

            CalibrationStep.GESTURES -> {
                onGestureFrame(frame, now)
                return
            }

            else -> Unit
        }

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

    /**
     * Aspetta che l'inquadratura sia buona, e che lo resti.
     *
     * Un istante buono non basta: la mano entra e esce, e partire sul primo
     * fotogramma fortunato riporterebbe esattamente al problema di prima.
     */
    private fun onFramingFrame(frame: HandFrame, now: Long) {
        if (framingWaitingSince == NOT_RECORDING) framingWaitingSince = now
        val hint = framingHint(frame.present, frame.handSpan, frame.palmX, frame.palmY)

        if (hint != FramingHint.OK) {
            framingOkSince = NOT_RECORDING
            _state.value = _state.value.copy(
                framing = hint,
                progress = 0f,
                framingStuck = now - framingWaitingSince > FRAMING_STUCK_AFTER_MS,
            )
            return
        }

        if (framingOkSince == NOT_RECORDING) framingOkSince = now
        val held = now - framingOkSince
        val progress = (held.toFloat() / FRAMING_HOLD_MS).coerceIn(0f, 1f)
        _state.value = _state.value.copy(framing = hint, progress = progress)

        if (progress >= 1f) enter(CalibrationStep.CENTER)
    }

    /**
     * Misura la mano ferma, e pretende che sia davvero ferma.
     *
     * Se durante la finestra la mano si sposta di molto, la finestra riparte:
     * altrimenti quello spostamento finirebbe dentro il tremore, e la zona
     * neutra uscirebbe larga il doppio del necessario - cioe' la mano dovrebbe
     * muoversi molto di piu' per far succedere qualcosa, senza che nessuno
     * sappia perche'.
     */
    private fun onCenterFrame(frame: HandFrame, now: Long) {
        if (xSamples.isNotEmpty()) {
            val driftX = abs(frame.palmX - xSamples.first())
            val driftY = abs(frame.palmY - ySamples.first())
            if (driftX > CENTER_MAX_DRIFT || driftY > CENTER_MAX_DRIFT) {
                clearSamples()
                recordingStartedAt = now
                _state.value = _state.value.copy(progress = 0f)
                return
            }
        }

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
            _state.value = _state.value.copy(result = partial)
            enter(CalibrationStep.GESTURES)
        }
    }

    /**
     * Prova i due comandi, uno alla volta.
     *
     * Il gesto va **tenuto**, non solo sfiorato: e' cosi' che funziona anche in
     * uso, e un riconoscimento che dura un fotogramma non dimostra niente.
     */
    private fun onGestureFrame(frame: HandFrame, now: Long) {
        val current = _state.value
        if (gestureWaitingSince == NOT_RECORDING) gestureWaitingSince = now

        val atteso = when (current.gestureStage) {
            GestureStage.THUMB -> HandSignal.THUMB_UP
            GestureStage.FIST -> HandSignal.CLOSED_FIST
            GestureStage.DONE -> return
        }

        val riconosciuto = frame.present &&
            frame.signal == atteso &&
            frame.signalConfidence >= GESTURE_MIN_CONFIDENCE

        if (!riconosciuto) {
            gestureHeldSince = NOT_RECORDING
            _state.value = current.copy(
                progress = 0f,
                // Dopo un po' si smette di far provare in silenzio: puo' essere
                // il gesto, puo' essere la luce, puo' essere questa mano. In
                // ogni caso l'utente deve poter andare avanti, e la pagella
                // dira' che quel gesto non e' stato riconosciuto.
                gestureStuck = now - gestureWaitingSince > GESTURE_STUCK_AFTER_MS,
            )
            return
        }

        if (gestureHeldSince == NOT_RECORDING) gestureHeldSince = now
        val progress = ((now - gestureHeldSince).toFloat() / GESTURE_HOLD_MS).coerceIn(0f, 1f)
        _state.value = current.copy(progress = progress)
        if (progress < 1f) return

        when (current.gestureStage) {
            GestureStage.THUMB -> {
                gestureHeldSince = NOT_RECORDING
                gestureWaitingSince = now
                _state.value = _state.value.copy(
                    thumbRecognised = true,
                    gestureStage = GestureStage.FIST,
                    progress = 0f,
                    gestureStuck = false,
                )
            }

            GestureStage.FIST -> {
                _state.value = _state.value.copy(
                    fistRecognised = true,
                    gestureStage = GestureStage.DONE,
                    progress = 1f,
                )
                enter(CalibrationStep.REPORT)
            }

            GestureStage.DONE -> Unit
        }
    }

    /** Salta il gesto che non viene riconosciuto, senza fingere che lo sia. */
    fun skipGesture() {
        gestureHeldSince = NOT_RECORDING
        gestureWaitingSince = NOT_RECORDING
        when (_state.value.gestureStage) {
            GestureStage.THUMB -> _state.value = _state.value.copy(
                gestureStage = GestureStage.FIST,
                progress = 0f,
                gestureStuck = false,
            )

            GestureStage.FIST, GestureStage.DONE -> enter(CalibrationStep.REPORT)
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

        /** Quanto va tenuta buona l'inquadratura prima di cominciare. */
        const val FRAMING_HOLD_MS = 1_800f

        /** Dopo quanto si offre di proseguire comunque. */
        const val FRAMING_STUCK_AFTER_MS = 12_000L

        /**
         * La finestra della mano ferma.
         *
         * Quattro secondi invece di due e mezzo: il tremore va misurato su un
         * campione che valga qualcosa, e due secondi e mezzo bastavano appena a
         * fermare la mano.
         */
        const val CENTER_DURATION_MS = 4_000f

        /** Oltre questo spostamento la finestra della mano ferma riparte. */
        const val CENTER_MAX_DRIFT = 0.055f

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

        /** Quanto va tenuto un gesto perche' conti come riconosciuto. */
        const val GESTURE_HOLD_MS = 900f
        const val GESTURE_MIN_CONFIDENCE = 0.5f

        /** Dopo quanto si offre di saltare il gesto che non viene visto. */
        const val GESTURE_STUCK_AFTER_MS = 10_000L
    }
}
