package dev.airscroll.core.settings

import dev.airscroll.core.common.model.HorizontalAction
import dev.airscroll.core.common.model.IndicatorCorner
import dev.airscroll.core.common.model.ScrollMode
import dev.airscroll.core.common.model.SituationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileTransferTest {

    private fun profiloVero() = AirScrollSettings(
        sensitivity = 1.4f,
        neutralZoneScale = 0.8f,
        maxScrollSpeedPxPerSec = 3000f,
        invertScroll = true,
        scrollMode = ScrollMode.SPEED,
        horizontalAction = HorizontalAction.NONE,
        maxVolumeStepsPerSec = 9f,
        waitingWindowMs = 8_000L,
        stopHoldMs = 1_500L,
        activationHoldMs = 600L,
        situationMode = SituationMode.CAR,
        indicatorCorner = IndicatorCorner.BOTTOM_START,
        hapticsEnabled = false,
        voiceEnabled = true,
        powerSaving = false,
        calibration = CalibrationProfile(
            completed = true,
            referenceHandSpan = 0.17f,
            tremor = 0.014f,
            reachUp = 0.19f,
            reachDown = 0.26f,
            reachLeft = 0.21f,
            reachRight = 0.30f,
        ).withDerivedRanges(),
    )

    @Test
    fun `un profilo esportato e reimportato e' lo stesso profilo`() {
        val originale = profiloVero()
        val riletto = ProfileTransfer.decode(ProfileTransfer.encode(originale), AirScrollSettings.Default)
        assertNotNull(riletto)
        requireNotNull(riletto)

        assertEquals(originale.calibration.reachUp, riletto.calibration.reachUp, 1e-6f)
        assertEquals(originale.calibration.reachDown, riletto.calibration.reachDown, 1e-6f)
        assertEquals(originale.calibration.reachLeft, riletto.calibration.reachLeft, 1e-6f)
        assertEquals(originale.calibration.reachRight, riletto.calibration.reachRight, 1e-6f)
        assertEquals(originale.calibration.tremor, riletto.calibration.tremor, 1e-6f)
        assertEquals(originale.calibration.referenceHandSpan, riletto.calibration.referenceHandSpan, 1e-6f)
        assertEquals(originale.sensitivity, riletto.sensitivity, 1e-6f)
        assertEquals(originale.neutralZoneScale, riletto.neutralZoneScale, 1e-6f)
        assertEquals(originale.maxScrollSpeedPxPerSec, riletto.maxScrollSpeedPxPerSec, 1e-6f)
        assertEquals(originale.scrollMode, riletto.scrollMode)
        assertEquals(originale.horizontalAction, riletto.horizontalAction)
        assertEquals(originale.situationMode, riletto.situationMode)
        assertEquals(originale.indicatorCorner, riletto.indicatorCorner)
        assertEquals(originale.invertScroll, riletto.invertScroll)
        assertEquals(originale.hapticsEnabled, riletto.hapticsEnabled)
        assertEquals(originale.voiceEnabled, riletto.voiceEnabled)
        assertEquals(originale.powerSaving, riletto.powerSaving)
        assertEquals(originale.waitingWindowMs, riletto.waitingWindowMs)
        assertEquals(originale.stopHoldMs, riletto.stopHoldMs)
        assertEquals(originale.activationHoldMs, riletto.activationHoldMs)
    }

    @Test
    fun `i riepiloghi vengono ricalcolati, non riletti`() {
        // Se un file rovinato dicesse "verticale = 0.9" con portate da 0.2, i
        // due numeri si contraddirebbero. Il riepilogo si ricava sempre dalle
        // portate vere.
        val riletto = requireNotNull(
            ProfileTransfer.decode(ProfileTransfer.encode(profiloVero()), AirScrollSettings.Default)
        )
        val atteso = (riletto.calibration.reachUp + riletto.calibration.reachDown) / 2f
        assertEquals(atteso, riletto.calibration.verticalRange, 1e-6f)
    }

    @Test
    fun `un file che non e' un profilo non viene applicato`() {
        // Meglio non fare niente che applicare mezzo file preso da chissa' dove.
        assertNull(ProfileTransfer.decode("ciao come stai", AirScrollSettings.Default))
        assertNull(ProfileTransfer.decode("", AirScrollSettings.Default))
        assertNull(ProfileTransfer.decode("sensibilita = 2.0", AirScrollSettings.Default))
    }

    @Test
    fun `una chiave sconosciuta non fa fallire l'import`() {
        // Un profilo scritto da una versione piu' recente deve restare
        // utilizzabile: si prende quello che si capisce e si ignora il resto.
        val conNovita = ProfileTransfer.encode(profiloVero()) + "\nfunzione_del_futuro = 42\n"
        val riletto = requireNotNull(ProfileTransfer.decode(conNovita, AirScrollSettings.Default))
        assertEquals(1.4f, riletto.sensitivity, 1e-6f)
    }

    @Test
    fun `una chiave assente lascia il valore che c'e' gia'`() {
        val minimo = """
            # AirScroll - profilo
            formato = 1
            sensibilita = 1.9
        """.trimIndent()
        val partenza = AirScrollSettings.Default.copy(neutralZoneScale = 1.7f)
        val riletto = requireNotNull(ProfileTransfer.decode(minimo, partenza))
        assertEquals(1.9f, riletto.sensitivity, 1e-6f)
        // Non azzerata: non era nel file, quindi non c'era niente da cambiare.
        assertEquals(1.7f, riletto.neutralZoneScale, 1e-6f)
    }

    @Test
    fun `un valore illeggibile vale come assente`() {
        val rovinato = """
            # AirScroll - profilo
            formato = 1
            sensibilita = molto
            modo = QUALCOSA_CHE_NON_ESISTE
            inverti = forse
        """.trimIndent()
        val partenza = AirScrollSettings.Default.copy(
            sensitivity = 1.1f,
            scrollMode = ScrollMode.FOLLOW,
            invertScroll = false,
        )
        val riletto = requireNotNull(ProfileTransfer.decode(rovinato, partenza))
        assertEquals(1.1f, riletto.sensitivity, 1e-6f)
        assertEquals(ScrollMode.FOLLOW, riletto.scrollMode)
        assertEquals(false, riletto.invertScroll)
    }

    @Test
    fun `l'import non riaccende il servizio`() {
        // Un profilo non decide se AirScroll deve essere acceso: quello lo
        // decide chi ha il telefono in mano, adesso.
        val acceso = ProfileTransfer.encode(profiloVero().copy(serviceEnabled = true))
        val riletto = requireNotNull(ProfileTransfer.decode(acceso, AirScrollSettings.Default))
        assertEquals(false, riletto.serviceEnabled)
    }

    @Test
    fun `il file si puo' leggere a occhio`() {
        // Non e' un vezzo: chi esporta il proprio profilo deve poter aprire il
        // file e vedere che dentro non c'e' niente che non si aspetta.
        val testo = ProfileTransfer.encode(profiloVero())
        assertTrue(testo.startsWith("# AirScroll"))
        assertTrue(testo.contains("cal_portata_su = 0.19"))
        assertTrue(testo.contains("sensibilita = 1.4"))
        // Nessun dato che non sia una scelta o una misura dell'utente.
        assertTrue(!testo.contains("kill"))
        assertTrue(!testo.contains("rebooted"))
    }
}
