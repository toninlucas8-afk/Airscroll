package dev.airscroll.core.gesture

import dev.airscroll.apps.api.AppCategory
import dev.airscroll.apps.api.AppProfile
import dev.airscroll.core.common.model.EngineState
import dev.airscroll.core.common.model.HandSignal
import dev.airscroll.core.settings.AirScrollSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il banco su cui si potranno tarare le soglie sui movimenti veri.
 *
 * Finche' non arriva una registrazione vera, la sessione qui sotto e'
 * sintetica: serve a provare che il meccanismo regge, non a tarare niente.
 * Quando arrivera' un file registrato davvero, entrera' qui dentro e da quel
 * momento ogni modifica alle soglie avra' una risposta invece di una speranza.
 */
class ReplayTest {

    private val profile = AppProfile(
        id = "dev.airscroll.replay",
        displayName = "Replay",
        category = AppCategory.READER,
        packageNames = setOf("dev.airscroll.replay"),
    )

    /**
     * Una sessione finta ma plausibile: si entra, si attiva col pollice, si
     * scorre due volte verso il basso, si torna al centro, si esce col pugno.
     */
    private fun sessione(): List<FlightRecorder.Sample> = buildList {
        var t = 0L
        fun aggiungi(
            millis: Long,
            signal: HandSignal,
            y: Float,
            present: Boolean = true,
        ) {
            repeat((millis / 50).toInt()) {
                add(
                    FlightRecorder.Sample(
                        timestampMs = t,
                        present = present,
                        signal = signal,
                        confidence = 0.9f,
                        palmX = 0.5f,
                        palmY = y,
                        handSpan = 0.16f,
                        // Lo stato e la velocita' registrati non entrano nel
                        // motore: e' il motore a doverli ricalcolare. Restano
                        // per poter confrontare cosa faceva la versione che ha
                        // registrato con cosa fa quella di adesso.
                        state = EngineState.WAITING,
                        scrollVelocity = 0f,
                    )
                )
                t += 50
            }
        }

        aggiungi(1_000, HandSignal.NONE, 0.5f, present = false)
        aggiungi(1_000, HandSignal.THUMB_UP, 0.5f)
        aggiungi(1_500, HandSignal.OPEN_PALM, 0.5f)
        aggiungi(2_000, HandSignal.OPEN_PALM, 0.72f)
        aggiungi(1_000, HandSignal.OPEN_PALM, 0.5f)
        aggiungi(2_000, HandSignal.OPEN_PALM, 0.74f)
        aggiungi(1_000, HandSignal.OPEN_PALM, 0.5f)
        aggiungi(2_500, HandSignal.CLOSED_FIST, 0.5f)
    }

    @Test
    fun `una sessione registrata si rigioca e produce un riassunto`() {
        val riassunto = Replay.run(sessione(), AirScrollSettings.Default, profile)

        assertEquals(sessione().size, riassunto.frames)
        assertTrue("doveva attivarsi almeno una volta", riassunto.activations >= 1)
        assertTrue("doveva restare attivo", riassunto.activeMs > 1_000)
        assertTrue("doveva scorrere", riassunto.scrolledPx > 100f)
    }

    @Test
    fun `rigiocare la stessa registrazione da' sempre lo stesso risultato`() {
        // Senza questo non si potrebbero confrontare due esecuzioni, che e'
        // l'unico motivo per cui il rigioco esiste. L'orologio e' quello della
        // registrazione, non quello di sistema.
        val primo = Replay.run(sessione(), AirScrollSettings.Default, profile)
        val secondo = Replay.run(sessione(), AirScrollSettings.Default, profile)
        assertEquals(primo, secondo)
    }

    @Test
    fun `una soglia diversa si vede nel riassunto`() {
        // E' la domanda a cui il rigioco serve a rispondere: cambiando una
        // soglia, sui movimenti veri cosa succede?
        val normale = Replay.run(sessione(), AirScrollSettings.Default, profile)
        val zonaNeutraLarga = Replay.run(
            sessione(),
            AirScrollSettings.Default.copy(neutralZoneScale = 4f),
            profile,
        )
        assertTrue(
            "con la zona neutra larghissima si doveva scorrere meno: " +
                "$normale contro $zonaNeutraLarga",
            zonaNeutraLarga.scrolledPx < normale.scrolledPx,
        )
    }

    @Test
    fun `una registrazione passata per il file e' la stessa registrazione`() {
        // La registrazione arriva come file, non come oggetti in memoria: il
        // giro completo - scrivi, rileggi, rigioca - deve dare lo stesso
        // risultato, altrimenti il file non serve a niente.
        val diretto = Replay.run(sessione(), AirScrollSettings.Default, profile)
        val dalFile = Replay.run(
            FlightRecordFormat.decode(FlightRecordFormat.encode(sessione(), "Test", "14")),
            AirScrollSettings.Default,
            profile,
        )
        assertEquals(diretto.activations, dalFile.activations)
        assertEquals(diretto.activeMs, dalFile.activeMs)
        assertEquals(diretto.scrolledPx, dalFile.scrolledPx, 1f)
    }

    @Test
    fun `una registrazione vuota non fa esplodere niente`() {
        val riassunto = Replay.run(emptyList(), AirScrollSettings.Default, profile)
        assertEquals(0, riassunto.frames)
        assertEquals(0, riassunto.activations)
    }
}
