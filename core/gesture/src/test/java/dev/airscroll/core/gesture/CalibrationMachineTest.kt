package dev.airscroll.core.gesture

import dev.airscroll.core.common.model.HandFrame
import dev.airscroll.core.common.model.HandSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Una calibrazione intera, dall'inizio alla pagella, senza telefono.
 *
 * E' il test che mancava a tutto il progetto. Prima, l'unico modo di sapere se
 * la calibrazione funzionava era installare l'APK e rifarla a mano: un minuto a
 * giro, e i casi limite - il gesto che non arriva mai, l'inquadratura che non
 * si stabilizza, la mano che scivola durante la misura - a mano non si
 * riproducono affatto.
 *
 * Qui una calibrazione completa costa un millisecondo, quindi si possono
 * provare anche le strade storte.
 */
class CalibrationMachineTest {

    /** Un pilota che finge una mano, con un orologio che avanza a comando. */
    private class Pilota(val machine: CalibrationMachine = CalibrationMachine()) {
        var now = 0L

        /** Manda fotogrammi identici per un certo tempo, a venti al secondo. */
        fun manda(
            millis: Long,
            present: Boolean = true,
            x: Float = 0.5f,
            y: Float = 0.5f,
            span: Float = 0.16f,
            signal: HandSignal = HandSignal.OPEN_PALM,
            confidence: Float = 0.9f,
        ) {
            val fine = now + millis
            while (now <= fine) {
                machine.onFrame(
                    HandFrame(
                        timestampMs = now,
                        present = present,
                        signal = signal,
                        signalConfidence = confidence,
                        palmX = x,
                        palmY = y,
                        handSpan = span,
                    ),
                    now,
                )
                now += 50
            }
        }

        /** Disegna un cerchio completo attorno al centro, come farebbe un braccio. */
        fun disegnaIlCerchio(raggio: Float = 0.22f, giri: Int = 2) {
            val passi = ReachMap.DEFAULT_SECTORS * 3 * giri
            repeat(passi) { passo ->
                val angolo = 2.0 * PI * passo / (ReachMap.DEFAULT_SECTORS * 3)
                manda(
                    millis = 0,
                    x = 0.5f + raggio * cos(angolo).toFloat(),
                    y = 0.5f + raggio * sin(angolo).toFloat(),
                )
            }
        }

        /** Il percorso normale fino al cerchio incluso. */
        fun finoAlCerchio() {
            machine.advance() // INTRO -> FRAMING
            manda(2_000)      // inquadratura buona e tenuta
            manda(4_500)      // mano ferma
            machine.advance() // CENTER -> RING
            disegnaIlCerchio()
        }
    }

    @Test
    fun `una calibrazione fatta bene arriva alla pagella con voti buoni`() {
        val pilota = Pilota()
        pilota.finoAlCerchio()

        // Il cerchio completo porta ai gesti da solo.
        assertEquals(CalibrationMachine.Step.GESTURES, pilota.machine.state.step)

        pilota.manda(1_200, signal = HandSignal.THUMB_UP)
        pilota.manda(1_200, signal = HandSignal.CLOSED_FIST)

        val finale = pilota.machine.state
        assertEquals(CalibrationMachine.Step.REPORT, finale.step)
        assertTrue(finale.thumbRecognised)
        assertTrue(finale.fistRecognised)

        val report = assertNotNull(finale.report).let { finale.report!! }
        assertEquals(Grade.GOOD, report.overall)
        assertNull(report.weakest)

        // Le quattro portate misurate sul cerchio vero, non i valori di partenza.
        val profilo = finale.profile
        assertTrue("portata su: ${profilo.reachUp}", profilo.reachUp > 0.15f)
        assertTrue("portata giu: ${profilo.reachDown}", profilo.reachDown > 0.15f)
        assertTrue("portata sinistra: ${profilo.reachLeft}", profilo.reachLeft > 0.15f)
        assertTrue("portata destra: ${profilo.reachRight}", profilo.reachRight > 0.15f)
    }

    @Test
    fun `senza mano l'inquadratura non parte mai`() {
        val pilota = Pilota()
        pilota.machine.advance()
        pilota.manda(5_000, present = false)

        assertEquals(CalibrationMachine.Step.FRAMING, pilota.machine.state.step)
        assertEquals(FramingHint.NO_HAND, pilota.machine.state.framing)
        assertEquals(0f, pilota.machine.state.progress, 1e-6f)
    }

    @Test
    fun `l'inquadratura dice cosa fare, non solo che non va`() {
        val pilota = Pilota()
        pilota.machine.advance()

        pilota.manda(500, span = 0.03f)
        assertEquals(FramingHint.TOO_FAR, pilota.machine.state.framing)

        pilota.manda(500, span = 0.5f)
        assertEquals(FramingHint.TOO_CLOSE, pilota.machine.state.framing)

        pilota.manda(500, x = 0.03f)
        assertEquals(FramingHint.OFF_CENTRE, pilota.machine.state.framing)
    }

    @Test
    fun `un'inquadratura buona a sprazzi non fa partire la misura`() {
        // Il caso che a mano non si riproduce: la mano entra ed esce dalla
        // posizione giusta. Senza il requisito di tenerla, basterebbe un
        // fotogramma fortunato.
        val pilota = Pilota()
        pilota.machine.advance()
        repeat(10) {
            pilota.manda(600)              // buona, ma non abbastanza a lungo
            pilota.manda(300, span = 0.03f) // e si allontana di nuovo
        }
        assertEquals(CalibrationMachine.Step.FRAMING, pilota.machine.state.step)
    }

    @Test
    fun `se l'inquadratura non si stabilizza mai si puo' proseguire`() {
        val pilota = Pilota()
        pilota.machine.advance()
        pilota.manda(13_000, span = 0.02f)

        assertTrue("doveva offrire una via d'uscita", pilota.machine.state.framingStuck)
        pilota.machine.advance()
        assertEquals(CalibrationMachine.Step.CENTER, pilota.machine.state.step)
    }

    @Test
    fun `la mano che scivola fa ripartire la misura del tremore`() {
        // Altrimenti quello spostamento finirebbe dentro il tremore, e la zona
        // neutra uscirebbe larga il doppio del necessario.
        val pilota = Pilota()
        pilota.machine.advance()
        pilota.manda(2_000)

        pilota.manda(3_000)              // quasi finita...
        pilota.manda(500, x = 0.75f)     // ...e la mano scivola via
        assertEquals(0f, pilota.machine.state.progress, 0.2f)
        assertEquals(CalibrationMachine.Step.CENTER, pilota.machine.state.step)
    }

    @Test
    fun `un tremolio vero finisce nella zona neutra`() {
        val pilota = Pilota()
        pilota.machine.advance()
        pilota.manda(2_000)

        // Mano ferma ma non immobile: un tremolio di pochi millesimi.
        var t = 0
        while (pilota.machine.state.progress < 1f && t < 200) {
            val oscillazione = if (t % 2 == 0) 0.004f else -0.004f
            pilota.manda(0, x = 0.5f + oscillazione, y = 0.5f - oscillazione)
            pilota.now += 50
            t++
        }
        val tremore = pilota.machine.state.profile.tremor
        assertTrue("tremore misurato: $tremore", tremore > CalibrationMachine.MIN_TREMOR)
        assertTrue("tremore misurato: $tremore", tremore <= CalibrationMachine.MAX_TREMOR)
    }

    @Test
    fun `un braccio sbilanciato produce portate diverse`() {
        // E' la ragione per cui il cerchio esiste: nessuno arriva alla stessa
        // distanza in tutte le direzioni.
        val pilota = Pilota()
        pilota.machine.advance()
        pilota.manda(2_000)
        pilota.manda(4_500)
        pilota.machine.advance()

        // Ellisse: largo a destra, corto a sinistra.
        val passi = ReachMap.DEFAULT_SECTORS * 6
        repeat(passi) { passo ->
            val angolo = 2.0 * PI * passo / (ReachMap.DEFAULT_SECTORS * 3)
            val cosA = cos(angolo).toFloat()
            val raggio = if (cosA > 0) 0.28f else 0.12f
            pilota.manda(0, x = 0.5f + raggio * cosA, y = 0.5f + 0.2f * sin(angolo).toFloat())
        }
        pilota.machine.advance() // conclude il cerchio anche se non e' pieno

        val profilo = pilota.machine.state.profile
        assertTrue(
            "destra ${profilo.reachRight} doveva superare sinistra ${profilo.reachLeft}",
            profilo.reachRight > profilo.reachLeft,
        )
    }

    @Test
    fun `un gesto che non arriva non blocca la calibrazione`() {
        val pilota = Pilota()
        pilota.finoAlCerchio()
        assertEquals(CalibrationMachine.Step.GESTURES, pilota.machine.state.step)

        // Undici secondi di mano aperta: il pollice non arriva mai.
        pilota.manda(11_000, signal = HandSignal.OPEN_PALM)
        assertTrue(pilota.machine.state.gestureStuck)

        pilota.machine.skipGesture()
        pilota.manda(1_200, signal = HandSignal.CLOSED_FIST)

        val finale = pilota.machine.state
        assertEquals(CalibrationMachine.Step.REPORT, finale.step)
        // La pagella dice la verita': un gesto non riconosciuto.
        assertFalse(finale.thumbRecognised)
        assertTrue(finale.fistRecognised)
        assertEquals(
            Grade.FAIR,
            finale.report!!.scores.first { it.aspect == Aspect.GESTURES }.grade,
        )
    }

    @Test
    fun `un gesto sfiorato non conta`() {
        val pilota = Pilota()
        pilota.finoAlCerchio()

        // Mezzo secondo di pollice, poi via: non basta.
        pilota.manda(500, signal = HandSignal.THUMB_UP)
        pilota.manda(500, signal = HandSignal.OPEN_PALM)
        assertFalse(pilota.machine.state.thumbRecognised)

        // Un pollice riconosciuto male non conta nemmeno se tenuto.
        pilota.manda(2_000, signal = HandSignal.THUMB_UP, confidence = 0.2f)
        assertFalse(pilota.machine.state.thumbRecognised)
    }

    @Test
    fun `rifare solo un pezzo non cancella gli altri`() {
        val pilota = Pilota()
        pilota.finoAlCerchio()
        pilota.manda(1_200, signal = HandSignal.THUMB_UP)
        pilota.manda(1_200, signal = HandSignal.CLOSED_FIST)

        val portateMisurate = pilota.machine.state.profile.reachRight
        val tremoreMisurato = pilota.machine.state.profile.tremor

        // Si rifa' solo la prova dei gesti.
        pilota.machine.redo(Aspect.GESTURES)
        assertEquals(CalibrationMachine.Step.GESTURES, pilota.machine.state.step)
        assertFalse("la prova ricomincia da zero", pilota.machine.state.thumbRecognised)

        // Le misure del cerchio e della mano ferma sono ancora li'.
        assertEquals(portateMisurate, pilota.machine.state.profile.reachRight, 1e-6f)
        assertEquals(tremoreMisurato, pilota.machine.state.profile.tremor, 1e-6f)
    }

    @Test
    fun `ricominciare da capo azzera tutto`() {
        val pilota = Pilota()
        pilota.finoAlCerchio()
        pilota.machine.restart()

        val stato = pilota.machine.state
        assertEquals(CalibrationMachine.Step.INTRO, stato.step)
        assertFalse(stato.thumbRecognised)
        assertNull(stato.report)
        assertEquals(ReachMap.DEFAULT_SECTORS, stato.sectorsLeft)
    }

    @Test
    fun `un buco di un fotogramma non fa ricominciare la misura`() {
        // La fotocamera perde la mano per un istante in continuazione: se ogni
        // buco azzerasse il cronometro, la misura non finirebbe mai.
        val pilota = Pilota()
        pilota.machine.advance()
        pilota.manda(2_000)

        repeat(20) {
            pilota.manda(150)
            pilota.manda(100, present = false) // buco breve
        }
        assertTrue(
            "la misura doveva avanzare: ${pilota.machine.state.progress}",
            pilota.machine.state.progress > 0.5f,
        )
    }
}
