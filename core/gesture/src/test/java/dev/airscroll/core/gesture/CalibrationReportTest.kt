package dev.airscroll.core.gesture

import dev.airscroll.core.settings.CalibrationProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationReportTest {

    /** Una calibrazione fatta bene: tutto dentro le soglie buone. */
    private fun buona() = CalibrationProfile(
        completed = true,
        referenceHandSpan = 0.16f,
        reachUp = 0.22f,
        reachDown = 0.20f,
        reachLeft = 0.24f,
        reachRight = 0.23f,
        tremor = 0.010f,
    ).withDerivedRanges()

    @Test
    fun `una calibrazione buona non ha niente da rifare`() {
        val report = reportFor(buona(), thumbRecognised = true, fistRecognised = true)
        assertEquals(Grade.GOOD, report.overall)
        assertNull(report.weakest)
        assertFalse(report.lopsided)
    }

    @Test
    fun `il voto complessivo e' il pezzo peggiore, non la media`() {
        // Sei aspetti perfetti e uno pessimo non fanno una calibrazione quasi
        // perfetta: fanno una calibrazione che in quel verso non funziona.
        val zoppa = buona().copy(reachUp = 0.04f)
        val report = reportFor(zoppa, thumbRecognised = true, fistRecognised = true)
        assertEquals(Grade.POOR, report.overall)
        assertEquals(Aspect.REACH_UP, report.weakest?.aspect)
    }

    @Test
    fun `il pezzo da rifare e' il peggiore, non il primo trovato`() {
        val mista = buona().copy(
            referenceHandSpan = 0.08f, // solo sufficiente
            reachDown = 0.03f, // pessimo
        )
        val report = reportFor(mista, thumbRecognised = true, fistRecognised = true)
        assertEquals(Grade.FAIR, report.scores.first { it.aspect == Aspect.FRAMING }.grade)
        assertEquals(Aspect.REACH_DOWN, report.weakest?.aspect)
    }

    @Test
    fun `una mano troppo vicina e' un problema quanto una troppo lontana`() {
        val lontana = reportFor(buona().copy(referenceHandSpan = 0.03f), true, true)
        val vicina = reportFor(buona().copy(referenceHandSpan = 0.5f), true, true)
        assertEquals(Grade.POOR, lontana.scores.first { it.aspect == Aspect.FRAMING }.grade)
        assertEquals(Grade.POOR, vicina.scores.first { it.aspect == Aspect.FRAMING }.grade)
    }

    @Test
    fun `il tremore fuori scala non passa`() {
        val tremante = reportFor(buona().copy(tremor = 0.045f), true, true)
        assertEquals(Grade.POOR, tremante.scores.first { it.aspect == Aspect.STILLNESS }.grade)
    }

    @Test
    fun `i gesti non riconosciuti valgono quanto una portata mancante`() {
        // E' la verifica che prima non esisteva: si poteva finire una
        // calibrazione impeccabile e scoprire solo in uso che il proprio
        // pollice in su non veniva mai visto.
        val nessuno = reportFor(buona(), thumbRecognised = false, fistRecognised = false)
        assertEquals(Grade.POOR, nessuno.overall)
        assertEquals(Aspect.GESTURES, nessuno.weakest?.aspect)

        val soloUno = reportFor(buona(), thumbRecognised = true, fistRecognised = false)
        assertEquals(Grade.FAIR, soloUno.scores.first { it.aspect == Aspect.GESTURES }.grade)
    }

    @Test
    fun `due versi molto diversi vengono segnalati`() {
        // E' esattamente la condizione che alla prova fa dire "in giu' funziona,
        // in su no": due portate legittime ma sbilanciate.
        val sbilanciata = buona().copy(reachUp = 0.30f, reachDown = 0.12f)
        assertTrue(reportFor(sbilanciata, true, true).lopsided)

        val simmetrica = buona().copy(reachUp = 0.22f, reachDown = 0.20f)
        assertFalse(reportFor(simmetrica, true, true).lopsided)
    }

    @Test
    fun `una portata a zero e' sbilanciata, non una divisione per zero`() {
        val rotta = buona().copy(reachLeft = 0f)
        val report = reportFor(rotta, true, true)
        assertTrue(report.lopsided)
        assertEquals(Grade.POOR, report.scores.first { it.aspect == Aspect.REACH_LEFT }.grade)
    }

    // --- inquadratura, prima di misurare -----------------------------------

    @Test
    fun `senza mano non si parte`() {
        assertEquals(
            FramingHint.NO_HAND,
            framingHint(present = false, handSpan = 0.16f, palmX = 0.5f, palmY = 0.5f),
        )
    }

    @Test
    fun `una mano nel posto giusto lascia partire`() {
        assertEquals(
            FramingHint.OK,
            framingHint(present = true, handSpan = 0.16f, palmX = 0.5f, palmY = 0.5f),
        )
    }

    @Test
    fun `troppo lontana e troppo vicina sono due consigli diversi`() {
        // Dire solo "non va bene" non aiuta: la mossa da fare e' opposta nei
        // due casi.
        assertEquals(
            FramingHint.TOO_FAR,
            framingHint(present = true, handSpan = 0.04f, palmX = 0.5f, palmY = 0.5f),
        )
        assertEquals(
            FramingHint.TOO_CLOSE,
            framingHint(present = true, handSpan = 0.5f, palmX = 0.5f, palmY = 0.5f),
        )
    }

    @Test
    fun `partire dal bordo falserebbe meta' delle direzioni`() {
        assertEquals(
            FramingHint.OFF_CENTRE,
            framingHint(present = true, handSpan = 0.16f, palmX = 0.05f, palmY = 0.5f),
        )
        assertEquals(
            FramingHint.OFF_CENTRE,
            framingHint(present = true, handSpan = 0.16f, palmX = 0.5f, palmY = 0.95f),
        )
    }

    @Test
    fun `la distanza viene guardata prima della posizione`() {
        // Una mano lontanissima e anche storta ha un problema solo di cui vale
        // la pena parlare per primo: e' lontana.
        assertEquals(
            FramingHint.TOO_FAR,
            framingHint(present = true, handSpan = 0.02f, palmX = 0.02f, palmY = 0.5f),
        )
    }

    @Test
    fun `il valore misurato viene riportato, non solo giudicato`() {
        // La pagella deve poter mostrare i numeri: un voto senza il dato dietro
        // non permette a nessuno di capire se il giudizio e' sensato.
        val report = reportFor(buona(), true, true)
        assertEquals(0.16f, report.scores.first { it.aspect == Aspect.FRAMING }.measured, 1e-6f)
        assertEquals(0.22f, report.scores.first { it.aspect == Aspect.REACH_UP }.measured, 1e-6f)
    }
}
