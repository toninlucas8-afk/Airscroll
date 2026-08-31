package dev.airscroll.core.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StillnessDetectorTest {

    private fun scena(valore: Int, dimensione: Int = 256) = IntArray(dimensione) { valore }

    @Test
    fun `il primo fotogramma si analizza sempre`() {
        assertTrue(StillnessDetector().shouldAnalyse(scena(120)))
    }

    @Test
    fun `una scena immobile viene saltata`() {
        val rilevatore = StillnessDetector()
        rilevatore.shouldAnalyse(scena(120))
        assertFalse("il secondo fotogramma identico va saltato", rilevatore.shouldAnalyse(scena(120)))
        assertFalse(rilevatore.shouldAnalyse(scena(120)))
    }

    /** Il rumore del sensore non deve far ripartire l'analisi a ogni fotogramma. */
    @Test
    fun `il rumore del sensore non conta come movimento`() {
        val rilevatore = StillnessDetector()
        rilevatore.shouldAnalyse(IntArray(256) { 120 })
        val conRumore = IntArray(256) { indice -> 120 + (indice % 3) - 1 }
        assertFalse("una variazione di un livello non e' movimento", rilevatore.shouldAnalyse(conRumore))
    }

    /** Una mano che entra nell'inquadratura cambia molto: va analizzata subito. */
    @Test
    fun `una mano che entra fa ripartire l'analisi`() {
        val rilevatore = StillnessDetector()
        rilevatore.shouldAnalyse(scena(120))
        rilevatore.shouldAnalyse(scena(120))
        val conMano = IntArray(256) { indice -> if (indice < 80) 210 else 120 }
        assertTrue("l'ingresso della mano va visto", rilevatore.shouldAnalyse(conMano))
    }

    /**
     * La rete di sicurezza: anche con la scena perfettamente immobile non si
     * puo' smettere di guardare per sempre.
     */
    @Test
    fun `ogni tanto analizza comunque`() {
        val rilevatore = StillnessDetector(maxSkipStreak = 3)
        rilevatore.shouldAnalyse(scena(120))
        assertFalse(rilevatore.shouldAnalyse(scena(120)))
        assertFalse(rilevatore.shouldAnalyse(scena(120)))
        assertFalse(rilevatore.shouldAnalyse(scena(120)))
        assertTrue("dopo tre salti si analizza", rilevatore.shouldAnalyse(scena(120)))
    }

    @Test
    fun `conta quanti fotogrammi ha risparmiato`() {
        val rilevatore = StillnessDetector()
        rilevatore.shouldAnalyse(scena(120))
        repeat(4) { rilevatore.shouldAnalyse(scena(120)) }
        assertEquals(4L, rilevatore.totalSkipped)
    }

    @Test
    fun `un cambio di dimensione dei campioni non manda in crisi`() {
        val rilevatore = StillnessDetector()
        assertTrue(rilevatore.shouldAnalyse(scena(120, dimensione = 256)))
        assertTrue("dimensione diversa: si riparte", rilevatore.shouldAnalyse(scena(120, dimensione = 64)))
    }
}
