package dev.airscroll.core.gesture

import dev.airscroll.apps.api.ScrollTuning
import dev.airscroll.core.common.model.HandFrame
import dev.airscroll.core.common.model.HandSignal
import dev.airscroll.core.settings.AirScrollSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MotionMapperTest {

    private val settings = AirScrollSettings.Default
    private val tuning = ScrollTuning.Default

    private fun frame(
        timestampMs: Long,
        x: Float = 0.5f,
        y: Float = 0.5f,
        signal: HandSignal = HandSignal.OPEN_PALM,
    ) = HandFrame(
        timestampMs = timestampMs,
        present = true,
        signal = signal,
        signalConfidence = 0.9f,
        palmX = x,
        palmY = y,
        handSpan = settings.calibration.referenceHandSpan,
    )

    @Test
    fun `la mano ferma non produce scorrimento`() {
        val mapper = MotionMapper()
        mapper.anchorTo(frame(0), 0)

        var timestamp = 0L
        repeat(10) { index ->
            timestamp += 40
            // Micro-oscillazione dentro la zona neutra.
            val jitter = if (index % 2 == 0) 0.004f else -0.004f
            val output = mapper.map(frame(timestamp, y = 0.5f + jitter), timestamp, settings, tuning)
            assertEquals(0f, output.scrollVelocityPxPerSec, 0f)
        }
    }

    @Test
    fun `mano alzata scorre verso l'alto e mano abbassata verso il basso`() {
        val up = MotionMapper().apply { anchorTo(frame(0), 0) }
            .map(frame(40, y = 0.2f), 40, settings, tuning)
        val down = MotionMapper().apply { anchorTo(frame(0), 0) }
            .map(frame(40, y = 0.8f), 40, settings, tuning)

        // Positivo = il dito invisibile scende, quindi la pagina risale.
        assertTrue("mano in su: ${up.scrollVelocityPxPerSec}", up.scrollVelocityPxPerSec < 0f)
        assertTrue("mano in giu': ${down.scrollVelocityPxPerSec}", down.scrollVelocityPxPerSec > 0f)
    }

    @Test
    fun `piu' ci si allontana dal centro piu' si accelera`() {
        fun speedFor(target: Float): Float {
            val mapper = MotionMapper()
            mapper.anchorTo(frame(0), 0)
            var timestamp = 0L
            var speed = 0f
            // Piu' fotogrammi per lasciare che il filtro raggiunga il valore.
            repeat(12) {
                timestamp += 40
                speed = abs(mapper.map(frame(timestamp, y = target), timestamp, settings, tuning)
                    .scrollVelocityPxPerSec)
            }
            return speed
        }

        val small = speedFor(0.44f)
        val medium = speedFor(0.34f)
        val large = speedFor(0.20f)

        assertTrue("$small -> $medium", medium > small)
        assertTrue("$medium -> $large", large > medium)
        assertTrue("il massimo non va superato", large <= settings.maxScrollSpeedPxPerSec + 1f)
    }

    @Test
    fun `il pugno chiuso sospende lo scorrimento`() {
        val mapper = MotionMapper()
        mapper.anchorTo(frame(0), 0)
        val output = mapper.map(
            frame(40, y = 0.2f, signal = HandSignal.CLOSED_FIST),
            40,
            settings,
            tuning,
        )
        assertEquals(0f, output.scrollVelocityPxPerSec, 0f)
    }

    @Test
    fun `il movimento verticale non cambia il volume`() {
        val mapper = MotionMapper()
        mapper.anchorTo(frame(0), 0)
        var timestamp = 0L
        var totalSteps = 0
        repeat(20) {
            timestamp += 40
            // Verticale ampio, orizzontale appena fuori dalla zona neutra:
            // l'asse dominante deve restare quello verticale.
            val output = mapper.map(
                frame(timestamp, x = 0.53f, y = 0.20f),
                timestamp,
                settings,
                tuning,
            )
            totalSteps += output.volumeSteps
        }
        assertEquals(0, totalSteps)
    }

    @Test
    fun `il movimento laterale ampio alza il volume`() {
        val mapper = MotionMapper()
        mapper.anchorTo(frame(0), 0)
        var timestamp = 0L
        var totalSteps = 0
        repeat(30) {
            timestamp += 40
            val output = mapper.map(frame(timestamp, x = 0.85f), timestamp, settings, tuning)
            totalSteps += output.volumeSteps
        }
        assertTrue("gradini accumulati: $totalSteps", totalSteps > 0)
    }
}
