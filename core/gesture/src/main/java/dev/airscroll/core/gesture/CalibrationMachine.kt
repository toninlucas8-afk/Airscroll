package dev.airscroll.core.gesture

import dev.airscroll.core.common.model.HandFrame
import dev.airscroll.core.common.model.HandSignal
import dev.airscroll.core.settings.CalibrationProfile
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * La calibrazione, senza Android.
 *
 * Prima questa macchina a stati viveva dentro il ViewModel, insieme alla
 * fotocamera e al riconoscitore, e quindi non poteva essere provata da
 * nessuna parte: l'unico modo di sapere se funzionava era installare l'APK e
 * rifare la calibrazione a mano. Una calibrazione dura un minuto, ha cinque
 * passi e una dozzina di casi limite - i gesti che non arrivano, l'inquadratura
 * che non si stabilizza mai, la mano che scivola durante la misura - e nessuno
 * li rifa' venti volte a mano.
 *
 * Qui dentro non c'e' niente di Android: entrano fotogrammi e un orologio,
 * escono stati. Cosi' un test puo' far girare **una calibrazione intera** in
 * un millisecondo, compresi i casi che a mano non si riproducono mai.
 *
 * Il ViewModel resta, e fa solo il suo mestiere: accendere la fotocamera e
 * portare gli stati allo schermo.
 */
class CalibrationMachine {

    /** I passi, nell'ordine in cui vengono presentati. */
    enum class Step {
        INTRO,

        /**
         * Prima di misurare: si controlla di poter misurare.
         *
         * Se la mano e' lontana, mezza fuori inquadratura o al buio, misurare
         * lo stesso produrrebbe un profilo sbagliato con l'aria di essere a
         * posto - e il difetto si scoprirebbe giorni dopo, dentro un'altra app.
         * Un metro storto e' peggio di nessun metro.
         */
        FRAMING,

        /** Mano ferma: tremore, dimensione apparente, punto di riposo. */
        CENTER,

        /** Il cerchio da completare: le quattro portate. */
        RING,

        /** I due comandi, provati sul serio su questa mano. */
        GESTURES,

        /** La pagella. */
        REPORT,
    }

    /** Quale dei due gesti si sta provando. */
    enum class GestureStage { THUMB, FIST, DONE }

    data class State(
        val step: Step = Step.INTRO,
        val recording: Boolean = false,
        val progress: Float = 0f,
        val handVisible: Boolean = false,
        val framing: FramingHint = FramingHint.NO_HAND,
        val framingStuck: Boolean = false,
        val sectors: List<Boolean> = List(ReachMap.DEFAULT_SECTORS) { false },
        val sectorsLeft: Int = ReachMap.DEFAULT_SECTORS,
        val handOffsetX: Float = 0f,
        val handOffsetY: Float = 0f,
        val canFinishEarly: Boolean = false,
        val gestureStage: GestureStage = GestureStage.THUMB,
        val thumbRecognised: Boolean = false,
        val fistRecognised: Boolean = false,
        val gestureStuck: Boolean = false,
        val profile: CalibrationProfile = CalibrationProfile.Default,
        val report: CalibrationReport? = null,
    )

    var state: State = State()
        private set

    private val reachMap = ReachMap()
    private val spanSamples = ArrayList<Float>(128)
    private val xSamples = ArrayList<Float>(128)
    private val ySamples = ArrayList<Float>(128)

    private var recordingStartedAt = NOT_SET
    private var lastHandSeenAt = 0L
    private var framingOkSince = NOT_SET
    private var framingWaitingSince = NOT_SET
    private var gestureHeldSince = NOT_SET
    private var gestureWaitingSince = NOT_SET

    // --- comandi dall'interfaccia -------------------------------------------

    /** Il pulsante "avanti". Non serve durante i passi che finiscono da soli. */
    fun advance() {
        when (state.step) {
            Step.INTRO -> enter(Step.FRAMING)
            Step.FRAMING -> enter(Step.CENTER)
            Step.CENTER -> enter(Step.RING)
            Step.RING -> {
                finishRing()
                enter(Step.GESTURES)
            }

            Step.GESTURES -> enter(Step.REPORT)
            Step.REPORT -> Unit
        }
    }

    /**
     * Rifa' solo il pezzo debole.
     *
     * E' il motivo per cui esiste la pagella: senza, davanti a una misura
     * storta l'unica scelta sarebbe rifare tutto da capo, cosa che nessuno fa -
     * e la misura storta resterebbe.
     */
    fun redo(aspect: Aspect) {
        when (aspect) {
            Aspect.FRAMING -> enter(Step.FRAMING)
            Aspect.STILLNESS -> enter(Step.CENTER)
            Aspect.REACH_UP, Aspect.REACH_DOWN, Aspect.REACH_LEFT, Aspect.REACH_RIGHT -> {
                reachMap.reset()
                enter(Step.RING)
            }

            Aspect.GESTURES -> {
                state = state.copy(thumbRecognised = false, fistRecognised = false)
                enter(Step.GESTURES)
            }
        }
    }

    /** Salta il gesto che non viene riconosciuto, senza fingere che lo sia. */
    fun skipGesture() {
        gestureHeldSince = NOT_SET
        gestureWaitingSince = NOT_SET
        when (state.gestureStage) {
            GestureStage.THUMB -> state = state.copy(
                gestureStage = GestureStage.FIST,
                progress = 0f,
                gestureStuck = false,
            )

            GestureStage.FIST, GestureStage.DONE -> enter(Step.REPORT)
        }
    }

    fun restart() {
        clearSamples()
        reachMap.reset()
        recordingStartedAt = NOT_SET
        framingOkSince = NOT_SET
        framingWaitingSince = NOT_SET
        gestureHeldSince = NOT_SET
        gestureWaitingSince = NOT_SET
        state = State()
    }

    // --- i fotogrammi -------------------------------------------------------

    fun onFrame(frame: HandFrame, nowMs: Long) {
        if (frame.present) lastHandSeenAt = nowMs
        val visible = frame.present || (nowMs - lastHandSeenAt) < HAND_GRACE_MS
        if (state.handVisible != visible) state = state.copy(handVisible = visible)
        if (!state.recording) return

        // L'inquadratura e la prova dei gesti hanno bisogno anche dei fotogrammi
        // senza mano: e' proprio l'assenza che va raccontata.
        when (state.step) {
            Step.FRAMING -> {
                onFramingFrame(frame, nowMs)
                return
            }

            Step.GESTURES -> {
                onGestureFrame(frame, nowMs)
                return
            }

            else -> Unit
        }

        if (!frame.present) {
            // Senza mano il cronometro si ferma: cosi' non si deve rincorrere
            // una barra che avanza da sola.
            if (nowMs - lastHandSeenAt > HAND_GRACE_MS) recordingStartedAt = NOT_SET
            return
        }

        if (recordingStartedAt == NOT_SET) {
            recordingStartedAt = nowMs
            if (state.step == Step.CENTER) clearSamples()
        }

        when (state.step) {
            Step.CENTER -> onCenterFrame(frame, nowMs)
            Step.RING -> onRingFrame(frame, nowMs)
            else -> Unit
        }
    }

    /**
     * Aspetta che l'inquadratura sia buona, e che lo **resti**.
     *
     * Un istante buono non basta: la mano entra ed esce, e partire sul primo
     * fotogramma fortunato riporterebbe al problema di prima.
     */
    private fun onFramingFrame(frame: HandFrame, nowMs: Long) {
        if (framingWaitingSince == NOT_SET) framingWaitingSince = nowMs
        val hint = framingHint(frame.present, frame.handSpan, frame.palmX, frame.palmY)

        if (hint != FramingHint.OK) {
            framingOkSince = NOT_SET
            state = state.copy(
                framing = hint,
                progress = 0f,
                framingStuck = nowMs - framingWaitingSince > FRAMING_STUCK_AFTER_MS,
            )
            return
        }

        if (framingOkSince == NOT_SET) framingOkSince = nowMs
        val progress = ((nowMs - framingOkSince).toFloat() / FRAMING_HOLD_MS).coerceIn(0f, 1f)
        state = state.copy(framing = hint, progress = progress)
        if (progress >= 1f) enter(Step.CENTER)
    }

    /**
     * Misura la mano ferma, e pretende che sia davvero ferma.
     *
     * Se durante la finestra la mano scivola via, la finestra riparte:
     * altrimenti quello spostamento finirebbe dentro il tremore, e la zona
     * neutra uscirebbe larga il doppio del necessario - cioe' la mano dovrebbe
     * muoversi molto di piu' per far succedere qualcosa, senza che nessuno
     * sappia perche'.
     */
    private fun onCenterFrame(frame: HandFrame, nowMs: Long) {
        if (xSamples.isNotEmpty()) {
            val driftX = abs(frame.palmX - xSamples.first())
            val driftY = abs(frame.palmY - ySamples.first())
            if (driftX > CENTER_MAX_DRIFT || driftY > CENTER_MAX_DRIFT) {
                clearSamples()
                recordingStartedAt = nowMs
                state = state.copy(progress = 0f)
                return
            }
        }

        spanSamples.add(frame.handSpan)
        xSamples.add(frame.palmX)
        ySamples.add(frame.palmY)

        val progress = ((nowMs - recordingStartedAt).toFloat() / CENTER_DURATION_MS).coerceIn(0f, 1f)
        state = state.copy(progress = progress)
        if (progress < 1f || spanSamples.size < MIN_SAMPLES) return

        val profile = state.profile.copy(
            referenceHandSpan = median(spanSamples).coerceIn(MIN_HAND_SPAN, MAX_HAND_SPAN),
            tremor = (maxOf(deviation(xSamples), deviation(ySamples)) * TREMOR_SAFETY)
                .coerceIn(MIN_TREMOR, MAX_TREMOR),
        )
        reachMap.centerOn(median(xSamples), median(ySamples))
        state = state.copy(recording = false, progress = 1f, profile = profile)
    }

    private fun onRingFrame(frame: HandFrame, nowMs: Long) {
        val position = reachMap.accept(frame.palmX, frame.palmY)
        val elapsed = nowMs - recordingStartedAt

        state = state.copy(
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
            enter(Step.GESTURES)
        }
    }

    /**
     * Prova i due comandi, uno alla volta.
     *
     * Il gesto va **tenuto**, non sfiorato: e' cosi' che funziona anche in uso,
     * e un riconoscimento che dura un fotogramma non dimostra niente.
     */
    private fun onGestureFrame(frame: HandFrame, nowMs: Long) {
        if (gestureWaitingSince == NOT_SET) gestureWaitingSince = nowMs

        val atteso = when (state.gestureStage) {
            GestureStage.THUMB -> HandSignal.THUMB_UP
            GestureStage.FIST -> HandSignal.CLOSED_FIST
            GestureStage.DONE -> return
        }

        val riconosciuto = frame.present &&
            frame.signal == atteso &&
            frame.signalConfidence >= GESTURE_MIN_CONFIDENCE

        if (!riconosciuto) {
            gestureHeldSince = NOT_SET
            state = state.copy(
                progress = 0f,
                // Dopo un po' si smette di far provare in silenzio: puo' essere
                // il gesto, la luce, o questa mano. In ogni caso si deve poter
                // andare avanti, e la pagella dira' che non e' stato
                // riconosciuto invece di fingere di si'.
                gestureStuck = nowMs - gestureWaitingSince > GESTURE_STUCK_AFTER_MS,
            )
            return
        }

        if (gestureHeldSince == NOT_SET) gestureHeldSince = nowMs
        val progress = ((nowMs - gestureHeldSince).toFloat() / GESTURE_HOLD_MS).coerceIn(0f, 1f)
        state = state.copy(progress = progress)
        if (progress < 1f) return

        when (state.gestureStage) {
            GestureStage.THUMB -> {
                gestureHeldSince = NOT_SET
                gestureWaitingSince = nowMs
                state = state.copy(
                    thumbRecognised = true,
                    gestureStage = GestureStage.FIST,
                    progress = 0f,
                    gestureStuck = false,
                )
            }

            GestureStage.FIST -> {
                state = state.copy(
                    fistRecognised = true,
                    gestureStage = GestureStage.DONE,
                    progress = 1f,
                )
                enter(Step.REPORT)
            }

            GestureStage.DONE -> Unit
        }
    }

    // --- passaggi -----------------------------------------------------------

    private fun enter(step: Step) {
        clearSamples()
        recordingStartedAt = NOT_SET
        framingOkSince = NOT_SET
        framingWaitingSince = NOT_SET
        gestureHeldSince = NOT_SET
        gestureWaitingSince = NOT_SET

        val registra = step == Step.FRAMING ||
            step == Step.CENTER ||
            step == Step.RING ||
            step == Step.GESTURES

        state = state.copy(
            step = step,
            recording = registra,
            progress = 0f,
            canFinishEarly = false,
            framingStuck = false,
            gestureStuck = false,
            gestureStage = if (step == Step.GESTURES) GestureStage.THUMB else state.gestureStage,
            report = if (step == Step.REPORT) {
                reportFor(
                    profile = state.profile.withDerivedRanges(),
                    thumbRecognised = state.thumbRecognised,
                    fistRecognised = state.fistRecognised,
                )
            } else {
                null
            },
        )
    }

    /**
     * Trasforma il cerchio in quattro portate.
     *
     * Gli spicchi rimasti spenti non diventano zero: si ripiega sul valore di
     * partenza. Una portata a zero renderebbe quel verso violentemente nervoso,
     * e un cerchio lasciato a meta' e' proprio il caso in cui non sappiamo
     * abbastanza per essere aggressivi.
     */
    private fun finishRing() {
        val measured = reachMap.toReach()
        state = state.copy(
            profile = state.profile.copy(
                reachUp = clampReach(measured.up, CalibrationProfile.DEFAULT_VERTICAL_RANGE),
                reachDown = clampReach(measured.down, CalibrationProfile.DEFAULT_VERTICAL_RANGE),
                reachLeft = clampReach(measured.left, CalibrationProfile.DEFAULT_HORIZONTAL_RANGE),
                reachRight = clampReach(measured.right, CalibrationProfile.DEFAULT_HORIZONTAL_RANGE),
            ).withDerivedRanges()
        )
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

    companion object {
        private const val NOT_SET = -1L

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
