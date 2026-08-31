package dev.airscroll.core.gesture

import dev.airscroll.core.common.model.EngineState
import dev.airscroll.core.common.model.HandFrame
import dev.airscroll.core.common.model.HandSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlightRecorderTest {

    private fun frame(t: Long, x: Float = 0.5f, present: Boolean = true) = HandFrame(
        timestampMs = t,
        present = present,
        signal = HandSignal.OPEN_PALM,
        signalConfidence = 0.8f,
        palmX = x,
        palmY = 0.5f,
        handSpan = 0.16f,
    )

    @Test
    fun `l'anello si sovrascrive invece di crescere`() {
        // Due minuti restano due minuti anche dopo un'ora di uso: e' la
        // differenza fra una scatola nera e una perdita di memoria.
        val recorder = FlightRecorder(capacity = 100)
        repeat(1_000) { i ->
            recorder.record(frame(i * 50L), EngineState.ACTIVE, 0f)
        }
        assertEquals(100, recorder.size)

        val campioni = recorder.snapshot()
        // Sono rimasti gli **ultimi** cento, non i primi.
        assertEquals(900 * 50L, campioni.first().timestampMs)
        assertEquals(999 * 50L, campioni.last().timestampMs)
    }

    @Test
    fun `la registrazione dice cosa ha visto e cosa ha deciso`() {
        // Sapere che la mano era a 0.3 non dice niente se non si sa che in quel
        // momento il motore non stava scorrendo.
        val recorder = FlightRecorder()
        recorder.record(frame(0, x = 0.3f), EngineState.WAITING, 0f)
        recorder.record(frame(50, x = 0.3f), EngineState.ACTIVE, -820f)

        val campioni = recorder.snapshot()
        assertEquals(EngineState.WAITING, campioni[0].state)
        assertEquals(EngineState.ACTIVE, campioni[1].state)
        assertEquals(-820f, campioni[1].scrollVelocity, 1e-3f)
    }

    @Test
    fun `si sa quanti secondi copre`() {
        val recorder = FlightRecorder()
        repeat(40) { i -> recorder.record(frame(i * 50L), EngineState.WAITING, 0f) }
        assertEquals(1.95f, recorder.spanSeconds(), 0.01f)
    }

    @Test
    fun `scritta e riletta e' la stessa registrazione`() {
        val recorder = FlightRecorder()
        recorder.record(frame(0, x = 0.42f), EngineState.WAITING, 0f)
        recorder.record(frame(50, present = false), EngineState.WAITING, 0f)
        recorder.record(frame(100, x = 0.61f), EngineState.ACTIVE, -1234.5f)

        val testo = FlightRecordFormat.encode(recorder.snapshot(), "Pixel", "14")
        val riletti = FlightRecordFormat.decode(testo)

        assertEquals(3, riletti.size)
        assertEquals(0.42f, riletti[0].palmX, 1e-4f)
        assertEquals(false, riletti[1].present)
        assertEquals(EngineState.ACTIVE, riletti[2].state)
        assertEquals(-1234.5f, riletti[2].scrollVelocity, 0.1f)
    }

    @Test
    fun `una riga tagliata non butta via la registrazione`() {
        // Un file arrivato per messaggio puo' avere l'ultima riga a meta':
        // perdere due minuti di dati veri per quello sarebbe assurdo.
        val recorder = FlightRecorder()
        repeat(5) { i -> recorder.record(frame(i * 50L), EngineState.ACTIVE, 100f) }
        val testo = FlightRecordFormat.encode(recorder.snapshot(), "Pixel", "14") + "1234,1,OPEN"

        assertEquals(5, FlightRecordFormat.decode(testo).size)
    }

    @Test
    fun `nel file non finisce niente che non sia un numero o un gesto`() {
        val recorder = FlightRecorder()
        recorder.record(frame(0), EngineState.ACTIVE, 0f)
        val testo = FlightRecordFormat.encode(recorder.snapshot(), "Pixel 8", "14")

        assertTrue(testo.startsWith("# airscroll-volo"))
        assertTrue(testo.contains("nessuna immagine"))
        // L'intestazione dichiara il telefono, che serve a interpretare i dati,
        // e nient'altro di personale.
        assertTrue(testo.contains("device=Pixel 8"))
    }
}
