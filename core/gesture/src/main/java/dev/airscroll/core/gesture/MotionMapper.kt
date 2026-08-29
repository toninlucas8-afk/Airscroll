package dev.airscroll.core.gesture

import dev.airscroll.apps.api.ScrollTuning
import dev.airscroll.core.common.math.Ema
import dev.airscroll.core.common.math.OneEuroFilter
import dev.airscroll.core.common.math.clamp
import dev.airscroll.core.common.math.progressiveResponse
import dev.airscroll.core.common.model.DistanceProfile
import dev.airscroll.core.common.model.HandFrame
import dev.airscroll.core.common.model.HandSignal
import dev.airscroll.core.common.model.HorizontalAction
import dev.airscroll.core.settings.AirScrollSettings
import kotlin.math.abs
import kotlin.math.sign

/** Asse su cui il movimento e' attualmente agganciato. */
enum class MotionAxis { NONE, VERTICAL, HORIZONTAL }

data class MotionOutput(
    val scrollVelocityPxPerSec: Float = 0f,
    val volumeSteps: Int = 0,
    val gain: Float = 1f,
    val axis: MotionAxis = MotionAxis.NONE,
)

/**
 * Traduce la posizione della mano in velocita' di scorrimento e gradini di volume.
 *
 * Idee guida:
 * - non ci sono comandi a scatti: la mano e' un dito invisibile appoggiato allo schermo;
 * - una zona neutra ricavata dal tremolio misurato in calibrazione assorbe le micro-oscillazioni;
 * - la risposta e' progressiva: `velocita' = massimo * escursione^gamma`;
 * - un asse alla volta, con isteresi, altrimenti scorrendo si cambierebbe volume per sbaglio;
 * - l'ancora scivola lentamente verso la mano quando si sta fermi, cosi' una postura che cambia
 *   nel tempo non produce uno scorrimento continuo indesiderato.
 */
class MotionMapper {

    private val xFilter = OneEuroFilter(minCutoff = 1.0f, beta = 1.5f)
    private val yFilter = OneEuroFilter(minCutoff = 1.0f, beta = 1.5f)
    private val spanEma = Ema(alpha = 0.12f)

    private var anchorX = 0.5f
    private var anchorY = 0.5f
    private var lastUpdateMs = 0L
    private var hasLastUpdate = false
    private var neutralSinceMs = 0L
    private var inNeutral = false
    private var volumeAccumulator = 0f
    private var lockedAxis = MotionAxis.NONE

    var lastGain: Float = 1f
        private set

    /** Fissa il punto di riposo sulla posizione attuale della mano. */
    fun anchorTo(frame: HandFrame, nowMs: Long) {
        xFilter.reset()
        yFilter.reset()
        anchorX = xFilter.filter(frame.palmX, nowMs)
        anchorY = yFilter.filter(frame.palmY, nowMs)
        lastUpdateMs = nowMs
        hasLastUpdate = true
        neutralSinceMs = nowMs
        inNeutral = true
        volumeAccumulator = 0f
        lockedAxis = MotionAxis.NONE
        spanEma.reset()
        if (frame.handSpan > 0f) spanEma.update(frame.handSpan)
    }

    fun reset() {
        xFilter.reset()
        yFilter.reset()
        spanEma.reset()
        lastUpdateMs = 0L
        hasLastUpdate = false
        neutralSinceMs = 0L
        inNeutral = false
        volumeAccumulator = 0f
        lockedAxis = MotionAxis.NONE
    }

    fun map(
        frame: HandFrame,
        nowMs: Long,
        settings: AirScrollSettings,
        tuning: ScrollTuning,
    ): MotionOutput {
        if (!frame.present) {
            lockedAxis = MotionAxis.NONE
            volumeAccumulator = 0f
            return MotionOutput(gain = lastGain)
        }

        val dtSeconds = if (hasLastUpdate) (nowMs - lastUpdateMs) / 1000f else 0f
        lastUpdateMs = nowMs
        hasLastUpdate = true

        val x = xFilter.filter(frame.palmX, nowMs)
        val y = yFilter.filter(frame.palmY, nowMs)
        val span = if (frame.handSpan > 0f) spanEma.update(frame.handSpan) else spanEma.value

        val gain = computeGain(span, settings)
        lastGain = gain

        val calibration = settings.calibration
        val neutral = (calibration.tremor * NEUTRAL_MULTIPLIER * settings.neutralZoneScale)
            .clamp(MIN_NEUTRAL, MAX_NEUTRAL)
        val verticalRange = calibration.verticalRange.coerceAtLeast(neutral * 3f)
        val horizontalRange = calibration.horizontalRange.coerceAtLeast(neutral * 3f)

        // Il pugno chiuso e' il gesto di uscita: mentre e' visibile non si scorre.
        if (frame.signal == HandSignal.CLOSED_FIST) {
            driftAnchor(x, y, dtSeconds, nowMs, forced = true)
            lockedAxis = MotionAxis.NONE
            return MotionOutput(gain = gain)
        }

        val deltaY = anchorY - y      // positivo = mano alzata
        val deltaX = x - anchorX      // positivo = mano verso destra dell'utente

        val verticalExcess = abs(deltaY) - neutral
        val horizontalExcess = abs(deltaX) - neutral
        val volumeAllowed =
            settings.horizontalAction == HorizontalAction.VOLUME && tuning.volumeEnabled

        lockedAxis = chooseAxis(verticalExcess, horizontalExcess, volumeAllowed)

        if (lockedAxis == MotionAxis.NONE) {
            driftAnchor(x, y, dtSeconds, nowMs, forced = false)
            volumeAccumulator = 0f
            return MotionOutput(gain = gain, axis = MotionAxis.NONE)
        }

        inNeutral = false

        return when (lockedAxis) {
            MotionAxis.VERTICAL -> {
                val excursion = ((verticalExcess / (verticalRange - neutral)) * gain).clamp(0f, 1f)
                var speed = settings.maxScrollSpeedPxPerSec *
                    tuning.speedMultiplier *
                    progressiveResponse(excursion, tuning.curveGamma)
                if (speed < MIN_SCROLL_SPEED) speed = MIN_SCROLL_SPEED

                // Mano in alto -> il dito invisibile sale -> la pagina scende.
                var velocity = -sign(deltaY) * speed
                if (settings.invertScroll != tuning.invertScroll) velocity = -velocity

                MotionOutput(
                    scrollVelocityPxPerSec = velocity,
                    gain = gain,
                    axis = MotionAxis.VERTICAL,
                )
            }

            MotionAxis.HORIZONTAL -> {
                val excursion = ((horizontalExcess / (horizontalRange - neutral)) * gain).clamp(0f, 1f)
                val stepsPerSecond = settings.maxVolumeStepsPerSec *
                    progressiveResponse(excursion, VOLUME_GAMMA) *
                    sign(deltaX)
                volumeAccumulator += stepsPerSecond * dtSeconds
                val whole = volumeAccumulator.toInt()
                volumeAccumulator -= whole
                MotionOutput(volumeSteps = whole, gain = gain, axis = MotionAxis.HORIZONTAL)
            }

            MotionAxis.NONE -> MotionOutput(gain = gain)
        }
    }

    /**
     * Un asse alla volta, con isteresi: per rubare il controllo all'asse gia'
     * agganciato l'altro deve superarlo di un buon margine.
     */
    private fun chooseAxis(
        verticalExcess: Float,
        horizontalExcess: Float,
        volumeAllowed: Boolean,
    ): MotionAxis {
        val verticalActive = verticalExcess > 0f
        val horizontalActive = volumeAllowed && horizontalExcess > 0f

        if (!verticalActive && !horizontalActive) return MotionAxis.NONE
        if (!horizontalActive) return MotionAxis.VERTICAL
        if (!verticalActive) return MotionAxis.HORIZONTAL

        return when (lockedAxis) {
            MotionAxis.VERTICAL ->
                if (horizontalExcess > verticalExcess * AXIS_HYSTERESIS) MotionAxis.HORIZONTAL
                else MotionAxis.VERTICAL

            MotionAxis.HORIZONTAL ->
                if (verticalExcess > horizontalExcess * AXIS_HYSTERESIS) MotionAxis.VERTICAL
                else MotionAxis.HORIZONTAL

            MotionAxis.NONE ->
                if (verticalExcess >= horizontalExcess) MotionAxis.VERTICAL else MotionAxis.HORIZONTAL
        }
    }

    private fun computeGain(span: Float, settings: AirScrollSettings): Float {
        val base = when (val profile = settings.distanceProfile) {
            DistanceProfile.AUTO -> {
                val reference = settings.calibration.referenceHandSpan
                if (span.isNaN() || span <= 0.001f || reference <= 0.001f) 1f
                else (reference / span).clamp(MIN_AUTO_GAIN, MAX_AUTO_GAIN)
            }

            else -> profile.fixedGain ?: 1f
        }
        return base * settings.sensitivity
    }

    private fun driftAnchor(x: Float, y: Float, dtSeconds: Float, nowMs: Long, forced: Boolean) {
        if (!inNeutral) {
            inNeutral = true
            neutralSinceMs = nowMs
        }
        val settled = forced || nowMs - neutralSinceMs >= DRIFT_DELAY_MS
        if (!settled || dtSeconds <= 0f) return
        val factor = (dtSeconds * DRIFT_RATE_PER_SEC).clamp(0f, 1f)
        anchorX += (x - anchorX) * factor
        anchorY += (y - anchorY) * factor
    }

    private companion object {
        const val NEUTRAL_MULTIPLIER = 1.8f
        const val MIN_NEUTRAL = 0.010f
        const val MAX_NEUTRAL = 0.090f
        const val MIN_SCROLL_SPEED = 130f
        const val VOLUME_GAMMA = 1.7f
        const val AXIS_HYSTERESIS = 1.35f
        const val DRIFT_DELAY_MS = 600L
        const val DRIFT_RATE_PER_SEC = 0.6f
        const val MIN_AUTO_GAIN = 0.55f
        const val MAX_AUTO_GAIN = 2.6f
    }
}
