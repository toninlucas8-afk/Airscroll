package dev.airscroll.core.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class ReachMapTest {

    private fun mappaCompletataConRaggio(raggio: Float): ReachMap {
        val mappa = ReachMap()
        mappa.centerOn(0.5f, 0.5f)
        // Un giro completo, a passi piccoli, come farebbe una mano.
        for (grado in 0 until 360 step 5) {
            val radianti = Math.toRadians(grado.toDouble()).toFloat()
            mappa.accept(
                x = 0.5f + raggio * cos(radianti),
                y = 0.5f - raggio * sin(radianti),
            )
        }
        return mappa
    }

    @Test
    fun `un giro completo accende tutti gli spicchi`() {
        val mappa = mappaCompletataConRaggio(0.15f)
        assertTrue("cerchio non completo: ${mappa.filledCount}/12", mappa.isComplete)
        assertEquals(1f, mappa.progress, 0.001f)
    }

    @Test
    fun `una mano che non si muove non accende niente`() {
        val mappa = ReachMap()
        mappa.centerOn(0.5f, 0.5f)
        repeat(50) { mappa.accept(0.5f + 0.01f, 0.5f - 0.01f) }
        assertEquals(0, mappa.filledCount)
        assertFalse(mappa.isComplete)
    }

    /**
     * Il caso che conta: chi arriva lontano in su e poco in giu' deve
     * ritrovarsi due numeri diversi, non la loro media.
     */
    @Test
    fun `misura portate diverse nei due versi verticali`() {
        val mappa = ReachMap()
        mappa.centerOn(0.5f, 0.5f)
        for (grado in 0 until 360 step 5) {
            val radianti = Math.toRadians(grado.toDouble()).toFloat()
            // In alto si arriva a 0.20, in basso solo a 0.08.
            val verso = sin(radianti)
            val raggio = if (verso > 0f) 0.20f else 0.08f
            mappa.accept(0.5f + raggio * cos(radianti), 0.5f - raggio * verso)
        }
        val portata = mappa.toReach()
        assertTrue("in alto misurato ${portata.up}", portata.up > 0.17f)
        assertTrue("in basso misurato ${portata.down}", portata.down < 0.10f)
        assertTrue("i due versi devono restare distinti", portata.up > portata.down * 1.8f)
    }

    /**
     * Chi ha poca mobilita' non deve restare bloccato davanti a un cerchio che
     * non si chiude: arrivare al bordo dell'inquadratura vale come "sono
     * arrivato fin dove posso".
     */
    @Test
    fun `arrivare al bordo dell'inquadratura accende lo spicchio`() {
        val mappa = ReachMap()
        // Ancora vicinissima al bordo alto: verso l'alto non c'e' spazio per
        // raggiungere il raggio pieno.
        mappa.centerOn(0.5f, 0.12f)
        val stato = mappa.accept(0.5f, 0.05f)
        assertTrue("il puntino deve risultare verso l'alto", stato.dy > 0f)
        assertEquals("lo spicchio in alto deve accendersi", 1, mappa.filledCount)
    }

    @Test
    fun `un movimento in diagonale conta su entrambi gli assi`() {
        val mappa = ReachMap()
        mappa.centerOn(0.5f, 0.5f)
        // 45 gradi, in alto a destra, raggio 0.20: circa 0.14 per asse.
        mappa.accept(0.5f + 0.1414f, 0.5f - 0.1414f)
        val portata = mappa.toReach()
        assertTrue("in alto: ${portata.up}", portata.up > 0.10f)
        assertTrue("a destra: ${portata.right}", portata.right > 0.10f)
    }
}
