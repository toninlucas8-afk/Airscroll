package dev.airscroll.core.gesture

import android.os.SystemClock
import dev.airscroll.apps.api.AppProfile
import dev.airscroll.apps.api.ScrollTuning
import dev.airscroll.core.common.model.EngineState
import dev.airscroll.core.common.model.EngineStatus
import dev.airscroll.core.common.model.HandFrame
import dev.airscroll.core.common.model.HandSignal
import dev.airscroll.core.common.model.ScrollCommand
import dev.airscroll.core.common.model.StateChangeReason
import dev.airscroll.core.common.model.VolumeCommand
import dev.airscroll.core.settings.AirScrollSettings
import kotlin.math.abs

/**
 * Macchina a stati di AirScroll.
 *
 *     DISABLED --(utente accende)--> IDLE
 *     IDLE     --(app compatibile in primo piano)--> WAITING   [fotocamera accesa]
 *     WAITING  --(pollice in su tenuto)--> ACTIVE
 *     WAITING  --(scadenza finestra)--> IDLE                   [fotocamera spenta]
 *     ACTIVE   --(pugno chiuso ~2s / uscita dall'app)--> IDLE
 *     ACTIVE   --(mano persa a lungo)--> WAITING
 *
 * La classe non conosce Android oltre a `SystemClock`: e' testabile passando un
 * clock finto.
 */
class GestureEngine(
    private val listener: Listener,
    private val clock: () -> Long = { SystemClock.uptimeMillis() },
) {

    interface Listener {
        fun onStatus(status: EngineStatus)
        fun onScroll(command: ScrollCommand)
        fun onVolume(command: VolumeCommand)
        /** Chiamato quando cambia il fabbisogno di fotocamera o la cadenza. */
        fun onCameraNeed(needed: Boolean, targetFps: Int)
        /** Feedback aptico opzionale sui cambi di stato importanti. */
        fun onHaptic()
    }

    private val mapper = MotionMapper()
    private val activationHold = HoldDetector(DEFAULT_ACTIVATION_HOLD_MS, GESTURE_GRACE_MS)
    private val stopHold = HoldDetector(DEFAULT_STOP_HOLD_MS, GESTURE_GRACE_MS)

    private var settings: AirScrollSettings = AirScrollSettings.Default
    private var profile: AppProfile? = null
    private var currentPackage: String? = null

    private var state: EngineState = EngineState.DISABLED
    private var stateSince: Long = 0L
    private var lastHandSeen: Long = 0L
    private var handPresent = false
    private var lastPublishedVelocity = Float.NaN
    private var lastCameraNeed: Boolean? = null
    private var lastCameraFps = -1

    val currentState: EngineState get() = state

    fun updateSettings(newSettings: AirScrollSettings) {
        settings = newSettings
        activationHold.requiredMs = newSettings.activationHoldMs
        stopHold.requiredMs = newSettings.stopHoldMs
        if (!newSettings.serviceEnabled && state != EngineState.DISABLED) {
            transition(EngineState.DISABLED, StateChangeReason.SERVICE_TOGGLED)
        } else if (newSettings.serviceEnabled && state == EngineState.DISABLED) {
            transition(EngineState.IDLE, StateChangeReason.SERVICE_TOGGLED)
            // Il servizio puo' accendersi mentre l'utente e' gia' dentro un'app
            // compatibile: in quel caso va armato subito, senza aspettare il
            // prossimo cambio di finestra.
            applyForegroundApp(force = true)
        } else {
            publishStatus(StateChangeReason.SERVICE_TOGGLED)
            publishCameraNeed()
        }
    }

    /** Nuova app in primo piano; [newProfile] e' null se non e' supportata o e' disattivata. */
    fun onForegroundApp(packageName: String?, newProfile: AppProfile?) {
        val changed = packageName != currentPackage || newProfile?.id != profile?.id
        currentPackage = packageName
        profile = newProfile
        if (state == EngineState.DISABLED) return
        applyForegroundApp(force = changed)
    }

    private fun applyForegroundApp(force: Boolean) {
        val active = profile
        if (active == null) {
            if (state != EngineState.IDLE) transition(EngineState.IDLE, StateChangeReason.APP_LEFT)
            return
        }
        if (!force) return

        if (active.armOnEnter) {
            transition(EngineState.WAITING, StateChangeReason.APP_ENTERED)
        } else {
            transition(EngineState.IDLE, StateChangeReason.APP_ENTERED)
        }
    }

    /** Riapre manualmente la finestra di attesa (scorciatoia, notifica, tile). */
    fun rearm() {
        if (state == EngineState.DISABLED) return
        if (profile == null) return
        transition(EngineState.WAITING, StateChangeReason.APP_ENTERED)
    }

    fun onFrame(frame: HandFrame) {
        val now = clock()
        handPresent = frame.present
        if (frame.present) lastHandSeen = now

        when (state) {
            EngineState.WAITING -> handleWaiting(frame, now)
            EngineState.ACTIVE -> handleActive(frame, now)
            else -> Unit
        }
    }

    /**
     * Da chiamare a cadenza fissa (~10 Hz). Gestisce le scadenze anche quando
     * non arrivano fotogrammi, per esempio se la fotocamera e' occupata da
     * un'altra app.
     */
    fun tick() {
        val now = clock()
        when (state) {
            EngineState.WAITING -> {
                if (now - stateSince >= settings.waitingWindowMs) {
                    transition(EngineState.IDLE, StateChangeReason.WAITING_TIMEOUT)
                }
            }

            EngineState.ACTIVE -> {
                val silence = now - lastHandSeen
                if (silence >= HAND_LOST_STOP_MS) publishScroll(ScrollCommand.Stopped)
                if (silence >= HAND_LOST_TIMEOUT_MS) {
                    transition(EngineState.WAITING, StateChangeReason.HAND_LOST)
                }
            }

            else -> Unit
        }
    }

    fun reportError(message: String?) {
        listener.onStatus(buildStatus(StateChangeReason.ERROR).copy(lastError = message))
    }

    private fun handleWaiting(frame: HandFrame, now: Long) {
        if (holdProgressed(frame, HandSignal.THUMB_UP, activationHold, now)) {
            mapper.anchorTo(frame, frame.timestampMs)
            stopHold.reset()
            transition(EngineState.ACTIVE, StateChangeReason.ACTIVATION_GESTURE)
        }
    }

    private fun handleActive(frame: HandFrame, now: Long) {
        if (holdProgressed(frame, HandSignal.CLOSED_FIST, stopHold, now)) {
            publishScroll(ScrollCommand.Stopped)
            transition(EngineState.IDLE, StateChangeReason.STOP_GESTURE)
            return
        }

        val tuning = profile?.tuning ?: ScrollTuning.Default
        val output = mapper.map(frame, frame.timestampMs, settings, tuning)

        // Un buco di qualche decina di millisecondi non e' un ripensamento.
        //
        // `tick()` prevedeva gia' una tolleranza prima di fermare lo
        // scorrimento, ma non entrava mai in gioco: il riconoscitore emette
        // comunque un fotogramma "mano assente", che arriva qui, produce
        // velocita' zero e ferma tutto sul colpo. Bastava che la mano uscisse
        // dall'inquadratura per un istante - cosa che succede di continuo
        // abbassandola, perche' il bordo basso del fotogramma e' vicino - e lo
        // scorrimento moriva a meta' gesto.
        val velocity = if (frame.present) {
            output.scrollVelocityPxPerSec
        } else {
            val coasting = now - lastHandSeen < HAND_BLINK_COAST_MS
            if (coasting) lastPublishedVelocity.takeIf { !it.isNaN() } ?: 0f else 0f
        }

        publishScroll(
            ScrollCommand(
                velocityPxPerSec = velocity,
                gripFractionX = tuning.gripFractionX,
                gripFractionY = tuning.gripFractionY,
            )
        )
        if (output.volumeSteps != 0) {
            listener.onVolume(VolumeCommand(steps = output.volumeSteps))
        }
    }

    /**
     * Fa avanzare il conteggio di un gesto tenuto, distinguendo due cose che si
     * assomigliano solo in superficie.
     *
     * **Un buco** - il modello per un fotogramma non riconosce niente, o il
     * punteggio scende - non e' un ripensamento: e' rumore. Viene tollerato
     * dalla finestra di grazia del rilevatore, altrimenti a dodici fotogrammi
     * al secondo un gesto di 400 ms si perderebbe di continuo.
     *
     * **Un altro gesto riconosciuto con convinzione** invece e' un ripensamento
     * vero: l'utente ha aperto la mano, ha fatto altro. Li' il conteggio si
     * azzera subito, senza aspettare la finestra di grazia.
     *
     * Sulla confidenza c'e' un'isteresi: severi per cominciare (una mano
     * qualunque nell'inquadratura non deve far partire lo scorrimento),
     * indulgenti per proseguire (una volta capito il gesto, pretendere lo
     * stesso punteggio a ogni fotogramma significa solo perderlo a meta').
     */
    private fun holdProgressed(
        frame: HandFrame,
        signal: HandSignal,
        detector: HoldDetector,
        nowMs: Long,
    ): Boolean {
        val contradicted = frame.present &&
            frame.signal != signal &&
            frame.signal != HandSignal.NONE &&
            frame.signalConfidence >= ENTER_CONFIDENCE
        if (contradicted) {
            detector.reset()
            return false
        }

        val threshold = if (detector.isHolding) CONTINUE_CONFIDENCE else ENTER_CONFIDENCE
        val matches = frame.present &&
            frame.signal == signal &&
            frame.signalConfidence >= threshold
        return detector.update(matches, nowMs)
    }

    private fun transition(target: EngineState, reason: StateChangeReason) {
        if (state == target) return
        state = target
        stateSince = clock()

        when (target) {
            EngineState.ACTIVE -> {
                lastHandSeen = stateSince
                activationHold.reset()
            }

            EngineState.WAITING -> {
                activationHold.reset()
                stopHold.reset()
                mapper.reset()
                publishScroll(ScrollCommand.Stopped)
            }

            EngineState.IDLE, EngineState.DISABLED -> {
                activationHold.reset()
                stopHold.reset()
                mapper.reset()
                handPresent = false
                publishScroll(ScrollCommand.Stopped)
            }
        }

        publishCameraNeed()
        publishStatus(reason)
        if (reason == StateChangeReason.ACTIVATION_GESTURE || reason == StateChangeReason.STOP_GESTURE) {
            listener.onHaptic()
        }
    }

    private fun publishCameraNeed() {
        val needed = state.isCameraNeeded && settings.serviceEnabled
        val fps = when (state) {
            EngineState.ACTIVE -> settings.performanceMode.activeFps
            EngineState.WAITING -> settings.performanceMode.waitingFps
            else -> 0
        }
        if (lastCameraNeed == needed && lastCameraFps == fps) return
        lastCameraNeed = needed
        lastCameraFps = fps
        listener.onCameraNeed(needed, fps.coerceAtLeast(1))
    }

    private fun publishScroll(command: ScrollCommand) {
        val velocity = command.velocityPxPerSec
        val previous = lastPublishedVelocity
        val meaningful = previous.isNaN() ||
            (velocity == 0f) != (previous == 0f) ||
            abs(velocity - previous) > VELOCITY_EPSILON
        if (!meaningful) return
        lastPublishedVelocity = velocity
        listener.onScroll(command)
    }

    private fun publishStatus(reason: StateChangeReason) {
        listener.onStatus(buildStatus(reason))
    }

    private fun buildStatus(reason: StateChangeReason) = EngineStatus(
        state = state,
        reason = reason,
        activePackage = currentPackage,
        activeProfileName = profile?.displayName,
        handPresent = handPresent,
        effectiveGain = mapper.lastGain,
    )

    private companion object {
        const val DEFAULT_ACTIVATION_HOLD_MS = 400L
        const val DEFAULT_STOP_HOLD_MS = 2_000L

        /** Dopo questo silenzio fermiamo lo scorrimento ma restiamo attivi. */
        const val HAND_LOST_STOP_MS = 450L

        /**
         * Quanto si prosegue per inerzia su un fotogramma senza mano.
         *
         * Corto di proposito: copre un fotogramma perso o una sbandata fuori
         * inquadratura, non una mano abbassata per smettere.
         */
        const val HAND_BLINK_COAST_MS = 260L

        /** Dopo questo silenzio torniamo in attesa. */
        const val HAND_LOST_TIMEOUT_MS = 4_000L

        const val VELOCITY_EPSILON = 18f

        /** Quanto puo' sparire un gesto senza che il conteggio riparta da zero. */
        const val GESTURE_GRACE_MS = 220L

        /** Confidenza richiesta per cominciare a contare un gesto tenuto. */
        const val ENTER_CONFIDENCE = 0.55f

        /** Confidenza sufficiente per proseguire un conteggio gia' iniziato. */
        const val CONTINUE_CONFIDENCE = 0.35f
    }
}
