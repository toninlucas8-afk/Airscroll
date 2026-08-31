package dev.airscroll.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Quando si apre il microfono, e - piu' importante - quando non si apre.
 *
 * Un microfono che si apre da solo e' la cosa che la gente teme davvero di
 * un'app come questa. Meta' di questi test verifica proprio i casi in cui
 * **non** deve aprirsi.
 */
class VoiceGateTest {

    @Test
    fun `la V va tenuta, non sfiorata`() {
        val gate = VoiceGate()
        // Un fotogramma solo non basta: la V passa per caso anche mentre si
        // muove la mano.
        assertFalse(gate.onFrame(victory = true, nowMs = 0))
        assertFalse(gate.onFrame(victory = true, nowMs = 300))
        assertEquals(VoiceGate.State.CLOSED, gate.state)

        assertTrue(gate.onFrame(victory = true, nowMs = 800))
        assertEquals(VoiceGate.State.OPEN, gate.state)
    }

    @Test
    fun `un gesto interrotto non apre niente`() {
        val gate = VoiceGate()
        gate.onFrame(victory = true, nowMs = 0)
        gate.onFrame(victory = false, nowMs = 300) // la mano si e' mossa
        assertFalse(gate.onFrame(victory = true, nowMs = 800))
        assertEquals(VoiceGate.State.CLOSED, gate.state)
    }

    @Test
    fun `senza la V non succede mai niente`() {
        val gate = VoiceGate()
        // Trenta secondi di mano aperta, pollici, pugni: il microfono resta
        // chiuso. E' la garanzia principale di tutto il sistema.
        var t = 0L
        while (t < 30_000) {
            assertFalse(gate.onFrame(victory = false, nowMs = t))
            t += 100
        }
        assertEquals(VoiceGate.State.CLOSED, gate.state)
    }

    @Test
    fun `il microfono si chiude da solo`() {
        val gate = VoiceGate()
        gate.onFrame(true, 0)
        gate.onFrame(true, 800)
        assertEquals(VoiceGate.State.OPEN, gate.state)

        gate.onFrame(false, 800 + VoiceGate.DEFAULT_WINDOW_MS - 1)
        assertEquals(VoiceGate.State.OPEN, gate.state)

        gate.onFrame(false, 800 + VoiceGate.DEFAULT_WINDOW_MS)
        assertEquals(VoiceGate.State.CLOSED, gate.state)
    }

    @Test
    fun `la V ancora alzata non riapre subito il microfono`() {
        // Senza la pausa, chi finisce di parlare con la mano ancora alzata
        // vedrebbe il microfono riaprirsi all'istante, e da fuori sembrerebbe
        // che non si chiuda mai.
        val gate = VoiceGate()
        gate.onFrame(true, 0)
        gate.onFrame(true, 800)
        gate.close(1_000)

        assertFalse(gate.onFrame(true, 1_100))
        assertFalse(gate.onFrame(true, 2_000))
        assertEquals(VoiceGate.State.CLOSED, gate.state)
    }

    @Test
    fun `passata la pausa si puo' riaprire`() {
        val gate = VoiceGate()
        gate.close(1_000)
        val dopoLaPausa = 1_000 + VoiceGate.DEFAULT_COOLDOWN_MS
        assertFalse(gate.onFrame(true, dopoLaPausa))
        assertTrue(gate.onFrame(true, dopoLaPausa + VoiceGate.DEFAULT_HOLD_MS))
    }

    @Test
    fun `il tempo rimasto si vede`() {
        val gate = VoiceGate()
        gate.onFrame(true, 0)
        gate.onFrame(true, 800)
        assertEquals(1f, gate.remaining(800), 0.01f)
        assertEquals(0.5f, gate.remaining(800 + VoiceGate.DEFAULT_WINDOW_MS / 2), 0.01f)
        gate.close(9_000)
        assertEquals(0f, gate.remaining(9_000), 0.001f)
    }

    @Test
    fun `spegnere la voce azzera anche la pausa`() {
        val gate = VoiceGate()
        gate.onFrame(true, 0)
        gate.onFrame(true, 800)
        gate.reset()
        assertEquals(VoiceGate.State.CLOSED, gate.state)
        // Nessuna pausa residua: si riparte pulito.
        assertFalse(gate.onFrame(true, 900))
        assertTrue(gate.onFrame(true, 1_700))
    }
}
