package dev.airscroll.core.settings

import dev.airscroll.core.common.model.HorizontalAction
import dev.airscroll.core.common.model.SituationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SituationPresetTest {

    private val base = AirScrollSettings.Default

    @Test
    fun `senza preset le impostazioni restano quelle dell'utente`() {
        val mie = base.copy(sensitivity = 1.4f, neutralZoneScale = 0.8f)
        assertEquals(mie, mie.effective)
    }

    /**
     * Il preset piega i valori dell'utente, non li sovrascrive: chi ha alzato
     * la sensibilita' deve restare piu' sensibile della media anche in cucina.
     */
    @Test
    fun `il preset piega i valori dell'utente invece di sostituirli`() {
        val prudente = base.copy(sensitivity = 0.6f, situationMode = SituationMode.KITCHEN)
        val nervoso = base.copy(sensitivity = 1.8f, situationMode = SituationMode.KITCHEN)
        assertTrue(
            "chi era piu' sensibile deve restarlo",
            nervoso.effective.sensitivity > prudente.effective.sensitivity,
        )
    }

    @Test
    fun `spegnere il preset restituisce esattamente i propri valori`() {
        val mie = base.copy(sensitivity = 1.4f, neutralZoneScale = 0.8f, stopHoldMs = 1_500L)
        val conPreset = mie.copy(situationMode = SituationMode.CAR)
        assertEquals(mie, conPreset.copy(situationMode = SituationMode.NONE).effective)
    }

    /**
     * L'auto e' il preset piu' severo, e non e' una preferenza estetica: li'
     * il braccio si muove di continuo per il volante e per il cambio, e niente
     * di tutto questo deve essere scambiato per un comando.
     */
    @Test
    fun `l'auto e' piu' severa di tutte le altre situazioni`() {
        val auto = base.copy(situationMode = SituationMode.CAR).effective
        val altre = listOf(SituationMode.KITCHEN, SituationMode.SHOWER, SituationMode.BATHROOM)
            .map { base.copy(situationMode = it).effective }

        altre.forEach { altra ->
            assertTrue(
                "zona neutra: auto ${auto.neutralZoneScale} vs ${altra.neutralZoneScale}",
                auto.neutralZoneScale >= altra.neutralZoneScale,
            )
            assertTrue(
                "attivazione: auto ${auto.activationHoldMs} vs ${altra.activationHoldMs}",
                auto.activationHoldMs >= altra.activationHoldMs,
            )
            assertTrue(
                "scorrimento: auto ${auto.maxScrollSpeedPxPerSec} vs ${altra.maxScrollSpeedPxPerSec}",
                auto.maxScrollSpeedPxPerSec <= altra.maxScrollSpeedPxPerSec,
            )
        }
    }

    /** Sotto la doccia non si reagisce in sei secondi. */
    @Test
    fun `la doccia allunga molto la finestra di attesa`() {
        val doccia = base.copy(situationMode = SituationMode.SHOWER).effective
        assertTrue(
            "finestra di attesa: ${doccia.waitingWindowMs} ms",
            doccia.waitingWindowMs >= base.waitingWindowMs + 8_000L,
        )
    }

    /** La zona neutra non puo' crescere all'infinito componendo i fattori. */
    @Test
    fun `la zona neutra resta entro un limite`() {
        val estremo = base.copy(neutralZoneScale = 3f, situationMode = SituationMode.CAR).effective
        assertTrue("zona neutra ${estremo.neutralZoneScale}", estremo.neutralZoneScale <= 4f)
    }

    @Test
    fun `un preset che vieta il volume lo spegne davvero`() {
        val senzaVolume = SituationMode.entries.firstOrNull { !it.volumeAllowed }
        if (senzaVolume == null) return  // oggi nessuno lo vieta: il test resta valido
        val piegate = base.copy(
            horizontalAction = HorizontalAction.VOLUME,
            situationMode = senzaVolume,
        ).effective
        assertEquals(HorizontalAction.NONE, piegate.horizontalAction)
    }
}
