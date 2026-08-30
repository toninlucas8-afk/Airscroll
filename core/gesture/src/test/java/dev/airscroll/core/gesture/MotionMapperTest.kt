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

    // --- Simmetria fra i due versi -----------------------------------------
    //
    // Alla prova su telefono lo scorrimento verso il basso funzionava e quello
    // verso l'alto no. Uno dei due motivi stava qui: l'ancora si fissa dove sta
    // la mano al pollice in su, e da li' i due versi non hanno lo stesso spazio
    // prima che la mano esca dall'inquadratura.

    /** Muovendo la mano di altrettanto, i due versi devono dare la stessa spinta. */
    @Test
    fun `con l'ancora in basso i due versi rispondono allo stesso modo`() {
        val movimento = 0.10f
        val ancora = 0.78f  // mano bassa: sopra ha spazio, sotto quasi niente

        val versoAlto = velocitaDopoMovimento(ancora, -movimento)
        val versoBasso = velocitaDopoMovimento(ancora, +movimento)

        assertTrue("verso l'alto deve scorrere", abs(versoAlto) > 0f)
        assertTrue("verso il basso deve scorrere", abs(versoBasso) > 0f)
        assertEquals("versi opposti", -1f, versoAlto.sign * versoBasso.sign, 0.001f)

        val rapporto = abs(versoAlto) / abs(versoBasso)
        assertTrue(
            "I due versi rispondono in modo troppo diverso: rapporto $rapporto " +
                "(alto ${abs(versoAlto)}, basso ${abs(versoBasso)})",
            rapporto in 0.5f..2f,
        )
    }

    /** Il verso stretto deve comunque poter arrivare a fondo scala. */
    @Test
    fun `il verso con poco spazio arriva alla velocita' massima`() {
        val ancora = 0.80f
        // Tutto lo spazio che la mano ha prima di uscire dall'inquadratura:
        // consumarlo per intero deve dare la spinta piena, altrimenti quel
        // verso resta lento comunque ci si sforzi. E' il difetto segnalato.
        val spazioDisponibile = 0.92f - ancora
        val velocita = abs(velocitaDopoMovimento(ancora, +spazioDisponibile))
        val massima = settings.maxScrollSpeedPxPerSec * tuning.speedMultiplier
        assertTrue(
            "Con tutto lo spazio disponibile la velocita' e' solo $velocita su $massima",
            velocita >= massima * 0.9f,
        )
    }

    private val Float.sign: Float get() = if (this > 0f) 1f else if (this < 0f) -1f else 0f

    /**
     * Fissa l'ancora, poi sposta la mano di [delta] e restituisce la velocita'
     * a regime. Il movimento e' graduale perche' il filtro anti-tremolio ha
     * bisogno di qualche fotogramma per seguirlo.
     */
    private fun velocitaDopoMovimento(ancora: Float, delta: Float): Float {
        val mapper = MotionMapper()
        mapper.anchorTo(frame(0, y = ancora), 0)
        var timestamp = 0L
        var ultima = 0f
        repeat(25) {
            timestamp += 40
            val output = mapper.map(frame(timestamp, y = ancora + delta), timestamp, settings, tuning)
            ultima = output.scrollVelocityPxPerSec
        }
        return ultima
    }

    // --- Portata per direzione ---------------------------------------------

    /**
     * Il motivo per cui il cerchio di calibrazione esiste.
     *
     * Chi arriva lontano in alto e poco in basso deve trovare le due direzioni
     * ugualmente pronte: un movimento piccolo verso il basso - dove la portata
     * misurata e' piccola - deve spingere quanto un movimento grande verso
     * l'alto. Con una portata media, il verso corto restava lento comunque.
     */
    @Test
    fun `usa la portata misurata di quella direzione, non una media`() {
        val asimmetrica = settings.copy(
            calibration = settings.calibration.copy(
                reachUp = 0.24f,
                reachDown = 0.08f,
                reachLeft = 0.20f,
                reachRight = 0.20f,
            )
        )

        // Ancora al centro: spazio in abbondanza da entrambe le parti, cosi'
        // l'aiuto al bordo non entra in gioco e si misura solo la portata.
        val versoAlto = abs(velocitaConImpostazioni(asimmetrica, 0.5f, -0.24f))
        val versoBasso = abs(velocitaConImpostazioni(asimmetrica, 0.5f, +0.08f))

        val massima = asimmetrica.maxScrollSpeedPxPerSec * tuning.speedMultiplier
        assertTrue(
            "in alto, alla sua portata piena: $versoAlto su $massima",
            versoAlto >= massima * 0.85f,
        )
        assertTrue(
            "in basso, alla sua portata piena: $versoBasso su $massima. " +
                "Con una media questo verso resterebbe lento.",
            versoBasso >= massima * 0.85f,
        )
    }

    private fun velocitaConImpostazioni(
        impostazioni: AirScrollSettings,
        ancora: Float,
        delta: Float,
    ): Float {
        val mapper = MotionMapper()
        mapper.anchorTo(frame(0, y = ancora), 0)
        var timestamp = 0L
        var ultima = 0f
        repeat(25) {
            timestamp += 40
            val output =
                mapper.map(frame(timestamp, y = ancora + delta), timestamp, impostazioni, tuning)
            ultima = output.scrollVelocityPxPerSec
        }
        return ultima
    }
}
