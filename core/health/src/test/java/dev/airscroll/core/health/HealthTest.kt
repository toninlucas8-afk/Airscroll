package dev.airscroll.core.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Le regole della diagnosi, provate una per una.
 *
 * Il valore di questo file sta tutto nei casi in cui il risultato giusto e'
 * `null`: un sistema che avvisa quando non serve viene messo a tacere, e allora
 * non avvisa nemmeno quando serve.
 */
class HealthTest {

    /** Tutto a posto: servizio acceso, vivo, con i suoi permessi. */
    private fun sano() = HealthSnapshot(
        serviceEnabled = true,
        serviceRunning = true,
        accessibilityConnected = true,
        cameraPermission = true,
        visionReady = true,
        indicatorEnabled = true,
        canDrawOverlay = true,
        batteryUnrestricted = true,
        nowMs = 100_000L,
    )

    @Test
    fun `un sistema sano non ha niente da dire`() {
        assertNull(diagnose(sano()))
    }

    @Test
    fun `con l'interruttore spento non c'e' nessun guasto`() {
        // Il caso piu' importante di tutti: l'utente ha spento AirScroll.
        // Segnalare che "il servizio non e' attivo" sarebbe come lamentarsi
        // che la luce e' spenta dopo aver premuto l'interruttore.
        val spento = sano().copy(
            serviceEnabled = false,
            serviceRunning = false,
            accessibilityConnected = false,
            cameraPermission = false,
            visionReady = false,
        )
        assertNull(diagnose(spento))
    }

    @Test
    fun `l'interruttore acceso e il servizio morto e' il guasto piu' grave`() {
        val ucciso = sano().copy(serviceRunning = false, accessibilityConnected = false)
        // Anche con l'accessibilita' giu', si parla del servizio: e' la causa,
        // l'altro e' l'effetto.
        assertEquals(Problem.SERVICE_KILLED, diagnose(ucciso))
        assertEquals(Severity.BLOCKING, Problem.SERVICE_KILLED.severity)
    }

    @Test
    fun `un servizio che sta partendo non e' un servizio morto`() {
        // Fra l'interruttore e il servizio elencato da Android passano dei
        // decimi di secondo: segnalarlo li' sarebbe un guasto inventato
        // proprio mentre l'utente sta facendo la cosa giusta.
        val inAvvio = sano().copy(serviceRunning = false, serviceStarting = true)
        assertNull(diagnose(inAvvio))

        // Scaduta l'attesa, se ancora non c'e', il guasto e' vero.
        val scaduta = inAvvio.copy(serviceStarting = false)
        assertEquals(Problem.SERVICE_KILLED, diagnose(scaduta))
    }

    @Test
    fun `l'accessibilita' spenta blocca tutto`() {
        assertEquals(Problem.ACCESSIBILITY_OFF, diagnose(sano().copy(accessibilityConnected = false)))
    }

    @Test
    fun `il permesso della fotocamera revocato viene prima del riconoscimento`() {
        val senzaPermesso = sano().copy(cameraPermission = false, visionReady = false)
        // Senza permesso il riconoscitore non puo' funzionare: dire "MediaPipe
        // e' rotto" manderebbe a cercare il guasto nel posto sbagliato.
        assertEquals(Problem.CAMERA_PERMISSION, diagnose(senzaPermesso))
    }

    @Test
    fun `il riconoscimento rotto viene segnalato`() {
        assertEquals(Problem.VISION_BROKEN, diagnose(sano().copy(visionReady = false)))
    }

    @Test
    fun `la fotocamera occupata conta solo se e' appena successo`() {
        val appena = sano().copy(lastCameraErrorAtMs = 100_000L - 3_000L)
        assertEquals(Problem.CAMERA_BUSY, diagnose(appena))

        val vecchio = sano().copy(lastCameraErrorAtMs = 100_000L - CAMERA_ERROR_TTL_MS - 1)
        assertNull(diagnose(vecchio))
    }

    @Test
    fun `un errore della fotocamera nel futuro viene ignorato`() {
        // L'orologio del telefono puo' saltare all'indietro. Un'eta' negativa
        // non deve accendere un avviso che poi non si spegne piu'.
        val futuro = sano().copy(lastCameraErrorAtMs = 100_000L + 5_000L)
        assertNull(diagnose(futuro))
    }

    @Test
    fun `l'indicatore senza permesso e' un guasto parziale`() {
        val senzaOverlay = sano().copy(canDrawOverlay = false)
        assertEquals(Problem.OVERLAY_MISSING, diagnose(senzaOverlay))
        assertEquals(Severity.DEGRADED, Problem.OVERLAY_MISSING.severity)
    }

    @Test
    fun `senza indicatore acceso il permesso mancante non interessa`() {
        val indicatoreSpento = sano().copy(indicatorEnabled = false, canDrawOverlay = false)
        assertNull(diagnose(indicatoreSpento))
    }

    @Test
    fun `la batteria si segnala solo dopo un morto vero`() {
        // Prima che succeda: sarebbe un avviso su quasi tutti i telefoni, per
        // un problema che magari non arrivera' mai.
        val soloTeoria = sano().copy(batteryUnrestricted = false, systemKills = 0)
        assertNull(diagnose(soloTeoria))

        // Dopo che e' successo: spiega una cosa gia' vista.
        val dopoIlFatto = sano().copy(batteryUnrestricted = false, systemKills = 1)
        assertEquals(Problem.BATTERY_RESTRICTED, diagnose(dopoIlFatto))
    }

    @Test
    fun `un guasto grave copre l'avviso sulla batteria`() {
        val entrambi = sano().copy(
            accessibilityConnected = false,
            batteryUnrestricted = false,
            systemKills = 3,
        )
        assertEquals(Problem.ACCESSIBILITY_OFF, diagnose(entrambi))
    }

    @Test
    fun `l'ordine di dichiarazione e' l'ordine di gravita'`() {
        // Se qualcuno riordina l'enum senza pensarci, questo test glielo dice:
        // la priorita' della diagnosi si legge da li'.
        val attesi = listOf(
            Problem.SERVICE_KILLED,
            Problem.ACCESSIBILITY_OFF,
            Problem.CAMERA_PERMISSION,
            Problem.VISION_BROKEN,
            Problem.CAMERA_BUSY,
            Problem.OVERLAY_MISSING,
            Problem.BATTERY_RESTRICTED,
        )
        assertEquals(attesi, Problem.entries.toList())
    }
}
