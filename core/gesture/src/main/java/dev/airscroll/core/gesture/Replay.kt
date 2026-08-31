package dev.airscroll.core.gesture

import dev.airscroll.apps.api.AppProfile
import dev.airscroll.core.common.model.EngineState
import dev.airscroll.core.common.model.EngineStatus
import dev.airscroll.core.common.model.HandFrame
import dev.airscroll.core.common.model.ScrollCommand
import dev.airscroll.core.common.model.VolumeCommand
import dev.airscroll.core.settings.AirScrollSettings
import kotlin.math.abs
import kotlin.math.max

/**
 * Rigioca una registrazione dentro il motore.
 *
 * E' il pezzo che trasforma una registrazione da "una cosa che si puo'
 * mandare" a "una cosa che serve a qualcosa". Le soglie di AirScroll - quanto
 * a lungo va tenuto un pollice, quanto deve muoversi una mano perche' conti,
 * quanto in fretta accelera lo scorrimento - sono tutte scelte ragionate e non
 * misurate. Cambiarne una e' una scommessa, finche' non si puo' rispondere a
 * questa domanda: *sui movimenti veri di chi la usa, cosa cambia?*
 *
 * Con una registrazione vera in mano, la risposta diventa un numero. Si
 * rigioca lo stesso minuto prima e dopo la modifica, e si confrontano i due
 * riassunti: quante volte si e' attivato, quanto tempo e' rimasto attivo,
 * quanto ha scorso in tutto.
 *
 * L'orologio non e' quello di sistema: e' il tempo scritto nella registrazione.
 * Cosi' un minuto di movimenti si rigioca in un millisecondo, e il risultato e'
 * lo stesso ogni volta.
 */
object Replay {

    /**
     * Cosa ha fatto il motore, in cinque numeri.
     *
     * Pochi e grossolani di proposito: servono a **confrontare** due esecuzioni,
     * non a descriverne una. Un riassunto che cambia di poco a ogni modifica
     * innocua non farebbe notare quella che conta.
     */
    data class Summary(
        val frames: Int,
        /** Quante volte il motore e' passato ad attivo. */
        val activations: Int,
        /** Millisecondi passati in stato attivo. */
        val activeMs: Long,
        /** Quanto contenuto e' stato scorso in tutto, in pixel. */
        val scrolledPx: Float,
        /** La velocita' piu' alta raggiunta. */
        val peakVelocityPxPerSec: Float,
    ) {
        /** Una riga sola, per poterlo confrontare a occhio in un test. */
        override fun toString(): String =
            "fotogrammi=$frames attivazioni=$activations attivo=${activeMs}ms " +
                "scorso=${scrolledPx.toInt()}px picco=${peakVelocityPxPerSec.toInt()}px/s"
    }

    fun run(
        samples: List<FlightRecorder.Sample>,
        settings: AirScrollSettings,
        profile: AppProfile,
    ): Summary {
        if (samples.isEmpty()) return Summary(0, 0, 0L, 0f, 0f)

        var now = samples.first().timestampMs
        var activations = 0
        var activeMs = 0L
        var scrolled = 0f
        var peak = 0f
        var statoPrecedente = EngineState.DISABLED
        var velocitaCorrente = 0f
        var ultimoIstante = now

        val listener = object : GestureEngine.Listener {
            override fun onStatus(status: EngineStatus) {
                if (status.state == EngineState.ACTIVE && statoPrecedente != EngineState.ACTIVE) {
                    activations++
                }
                statoPrecedente = status.state
            }

            override fun onScroll(command: ScrollCommand) {
                velocitaCorrente = command.velocityPxPerSec
                peak = max(peak, abs(command.velocityPxPerSec))
            }

            override fun onVolume(command: VolumeCommand) = Unit
            override fun onCameraNeed(needed: Boolean, targetFps: Int) = Unit
            override fun onHaptic() = Unit
        }

        val engine = GestureEngine(listener) { now }
        // Il servizio va acceso qui e non da chi chiama: rigiocare una
        // registrazione vuol dire per definizione rivivere una sessione in cui
        // AirScroll era acceso. Lasciarlo decidere fuori significa solo poterlo
        // sbagliare, e il sintomo - un riassunto tutto a zero - non dice
        // perche'.
        engine.updateSettings(settings.copy(serviceEnabled = true))
        engine.onForegroundApp(profile.id, profile)

        samples.forEach { sample ->
            val dt = (sample.timestampMs - ultimoIstante).coerceAtLeast(0L)
            // Il tempo passato **prima** di questo fotogramma va contato con lo
            // stato e la velocita' che c'erano allora, non con quelli nuovi.
            if (statoPrecedente == EngineState.ACTIVE) activeMs += dt
            scrolled += abs(velocitaCorrente) * dt / 1000f

            now = sample.timestampMs
            ultimoIstante = sample.timestampMs
            engine.onFrame(sample.toFrame())
            engine.tick()
        }

        return Summary(
            frames = samples.size,
            activations = activations,
            activeMs = activeMs,
            scrolledPx = scrolled,
            peakVelocityPxPerSec = peak,
        )
    }

    private fun FlightRecorder.Sample.toFrame() = HandFrame(
        timestampMs = timestampMs,
        present = present,
        signal = signal,
        signalConfidence = confidence,
        palmX = palmX,
        palmY = palmY,
        handSpan = handSpan,
    )
}
