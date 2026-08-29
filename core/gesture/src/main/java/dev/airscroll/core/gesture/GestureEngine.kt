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
    private val activationHold = HoldDetector(DEFAULT_ACTIVATION_HOLD_MS)
    private val stopHold = HoldDetector(DEFAULT_STOP_HOLD_MS)

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
        val wantsActivation = frame.present && frame.signal == HandSignal.THUMB_UP
        if (activationHold.update(wantsActivation, now)) {
            mapper.anchorTo(frame, frame.timestampMs)
            stopHold.reset()
            transition(EngineState.ACTIVE, StateChangeReason.ACTIVATION_GESTURE)
        }
    }

    private fun handleActive(frame: HandFrame, now: Long) {
        val fistHeld = frame.present && frame.signal == HandSignal.CLOSED_FIST
        if (stopHold.update(fistHeld, now)) {
            publishScroll(ScrollCommand.Stopped)
            transition(EngineState.IDLE, StateChangeReason.STOP_GESTURE)
            return
        }

        val tuning = profile?.tuning ?: ScrollTuning.Default
        val output = mapper.map(frame, frame.timestampMs, settings, tuning)

        publishScroll(
            ScrollCommand(
                velocityPxPerSec = output.scrollVelocityPxPerSec,
                gripFractionX = tuning.gripFractionX,
                gripFractionY = tuning.gripFractionY,
            )
        )
        if (output.volumeSteps != 0) {
            listener.onVolume(VolumeCommand(steps = output.volumeSteps))
        }
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

        /** Dopo questo silenzio torniamo in attesa. */
        const val HAND_LOST_TIMEOUT_MS = 4_000L

        const val VELOCITY_EPSILON = 18f
    }
}
