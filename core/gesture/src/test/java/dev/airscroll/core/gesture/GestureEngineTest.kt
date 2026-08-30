package dev.airscroll.core.gesture

import dev.airscroll.apps.api.AppCategory
import dev.airscroll.apps.api.AppProfile
import dev.airscroll.core.common.model.EngineState
import dev.airscroll.core.common.model.EngineStatus
import dev.airscroll.core.common.model.HandFrame
import dev.airscroll.core.common.model.HandSignal
import dev.airscroll.core.common.model.ScrollCommand
import dev.airscroll.core.common.model.VolumeCommand
import dev.airscroll.core.settings.AirScrollSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureEngineTest {

    private var now = 0L
    private val cameraNeeds = mutableListOf<Pair<Boolean, Int>>()
    private val scrolls = mutableListOf<ScrollCommand>()
    private var status = EngineStatus()

    private val listener = object : GestureEngine.Listener {
        override fun onStatus(status: EngineStatus) {
            this@GestureEngineTest.status = status
        }

        override fun onScroll(command: ScrollCommand) {
            scrolls += command
        }

        override fun onVolume(command: VolumeCommand) = Unit

        override fun onCameraNeed(needed: Boolean, targetFps: Int) {
            cameraNeeds += needed to targetFps
        }

        override fun onHaptic() = Unit
    }

    private val engine = GestureEngine(listener) { now }

    private val profile = AppProfile(
        id = "test.app",
        displayName = "Test",
        packageNames = setOf("com.test"),
        category = AppCategory.BROWSER,
    )

    private fun enable() {
        engine.updateSettings(AirScrollSettings.Default.copy(serviceEnabled = true))
    }

    private fun frame(
        signal: HandSignal,
        y: Float = 0.5f,
        confidence: Float = 0.95f,
    ) = HandFrame(
        timestampMs = now,
        present = true,
        signal = signal,
        signalConfidence = confidence,
        palmX = 0.5f,
        palmY = y,
        handSpan = 0.14f,
    )

    @Test
    fun `da spento si passa a inattivo con la fotocamera chiusa`() {
        enable()
        assertEquals(EngineState.IDLE, engine.currentState)
        assertTrue(cameraNeeds.isEmpty() || cameraNeeds.last().first.not())
    }

    @Test
    fun `entrare in un'app compatibile apre la finestra di attesa`() {
        enable()
        engine.onForegroundApp("com.test", profile)

        assertEquals(EngineState.WAITING, engine.currentState)
        assertEquals(true, cameraNeeds.last().first)
        assertEquals(AirScrollSettings.Default.performanceMode.waitingFps, cameraNeeds.last().second)
    }

    @Test
    fun `entrare in un'app non supportata lascia la fotocamera chiusa`() {
        enable()
        engine.onForegroundApp("com.altro", null)

        assertEquals(EngineState.IDLE, engine.currentState)
        assertFalse(cameraNeeds.last().first)
    }

    @Test
    fun `il pollice in su tenuto abbastanza attiva il motore`() {
        enable()
        engine.onForegroundApp("com.test", profile)

        engine.onFrame(frame(HandSignal.THUMB_UP))
        assertEquals(EngineState.WAITING, engine.currentState)

        now += 500
        engine.onFrame(frame(HandSignal.THUMB_UP))
        assertEquals(EngineState.ACTIVE, engine.currentState)
        assertEquals(AirScrollSettings.Default.performanceMode.activeFps, cameraNeeds.last().second)
    }

    @Test
    fun `un pollice in su poco convinto non attiva nulla`() {
        enable()
        engine.onForegroundApp("com.test", profile)

        // Sotto la soglia di ingresso: potrebbe essere una mano qualsiasi.
        engine.onFrame(frame(HandSignal.THUMB_UP, confidence = 0.40f))
        now += 800
        engine.onFrame(frame(HandSignal.THUMB_UP, confidence = 0.40f))

        assertEquals(EngineState.WAITING, engine.currentState)
    }

    @Test
    fun `una volta iniziato il gesto resiste a un calo di confidenza`() {
        enable()
        engine.onForegroundApp("com.test", profile)

        // Parte convinto...
        engine.onFrame(frame(HandSignal.THUMB_UP, confidence = 0.90f))
        now += 200
        // ...poi il punteggio cala, ma resta sopra la soglia di prosecuzione.
        engine.onFrame(frame(HandSignal.THUMB_UP, confidence = 0.40f))
        now += 250
        engine.onFrame(frame(HandSignal.THUMB_UP, confidence = 0.38f))

        assertEquals(
            "un calo di punteggio non deve far perdere l'attivazione",
            EngineState.ACTIVE,
            engine.currentState,
        )
    }

    @Test
    fun `un fotogramma perso non fa perdere l'attivazione`() {
        enable()
        engine.onForegroundApp("com.test", profile)

        engine.onFrame(frame(HandSignal.THUMB_UP))
        now += 120
        // Un fotogramma in cui il modello non riconosce niente: capita.
        engine.onFrame(frame(HandSignal.NONE, confidence = 0f))
        now += 300
        engine.onFrame(frame(HandSignal.THUMB_UP))

        assertEquals(EngineState.ACTIVE, engine.currentState)
    }

    @Test
    fun `cambiare gesto azzera il conteggio, a differenza di un buco`() {
        enable()
        engine.onForegroundApp("com.test", profile)

        engine.onFrame(frame(HandSignal.THUMB_UP))
        now += 200
        // Mano aperta riconosciuta con convinzione: non e' un fotogramma perso,
        // e' l'utente che ha fatto altro. Il conteggio deve ripartire da zero.
        engine.onFrame(frame(HandSignal.OPEN_PALM))
        now += 400
        engine.onFrame(frame(HandSignal.THUMB_UP))

        assertEquals(EngineState.WAITING, engine.currentState)
    }

    @Test
    fun `la finestra di attesa scade e la fotocamera si chiude`() {
        enable()
        engine.onForegroundApp("com.test", profile)

        now += AirScrollSettings.Default.waitingWindowMs + 1
        engine.tick()

        assertEquals(EngineState.IDLE, engine.currentState)
        assertFalse(cameraNeeds.last().first)
    }

    @Test
    fun `il pugno chiuso tenuto due secondi ferma tutto`() {
        enable()
        engine.onForegroundApp("com.test", profile)
        now += 500
        engine.onFrame(frame(HandSignal.THUMB_UP))
        now += 500
        engine.onFrame(frame(HandSignal.THUMB_UP))
        assertEquals(EngineState.ACTIVE, engine.currentState)

        engine.onFrame(frame(HandSignal.CLOSED_FIST))
        now += AirScrollSettings.Default.stopHoldMs + 1
        engine.onFrame(frame(HandSignal.CLOSED_FIST))

        assertEquals(EngineState.IDLE, engine.currentState)
        assertEquals(0f, scrolls.last().velocityPxPerSec, 0f)
        assertFalse(cameraNeeds.last().first)
    }

    @Test
    fun `uscire dall'app disattiva e spegne la fotocamera`() {
        enable()
        engine.onForegroundApp("com.test", profile)
        now += 500
        engine.onFrame(frame(HandSignal.THUMB_UP))
        now += 500
        engine.onFrame(frame(HandSignal.THUMB_UP))
        assertEquals(EngineState.ACTIVE, engine.currentState)

        engine.onForegroundApp("com.launcher", null)

        assertEquals(EngineState.IDLE, engine.currentState)
        assertFalse(cameraNeeds.last().first)
    }

    @Test
    fun `spegnere il servizio riporta tutto a zero`() {
        enable()
        engine.onForegroundApp("com.test", profile)
        engine.updateSettings(AirScrollSettings.Default.copy(serviceEnabled = false))

        assertEquals(EngineState.DISABLED, engine.currentState)
        assertFalse(cameraNeeds.last().first)
        assertEquals(EngineState.DISABLED, status.state)
    }
}
