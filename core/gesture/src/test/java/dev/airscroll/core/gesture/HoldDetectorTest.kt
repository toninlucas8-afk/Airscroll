package dev.airscroll.core.gesture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldDetectorTest {

    @Test
    fun `scatta una sola volta dopo la soglia`() {
        val detector = HoldDetector(requiredMs = 400L)
        assertFalse(detector.update(true, 0L))
        assertFalse(detector.update(true, 399L))
        assertTrue(detector.update(true, 400L))
        assertFalse("non deve ripetersi", detector.update(true, 900L))
    }

    @Test
    fun `un buco breve non azzera il conteggio`() {
        // Il caso reale: a 12 fotogrammi al secondo il punteggio del gesto
        // scende sotto soglia per un fotogramma solo. Senza tolleranza
        // l'attivazione non scatterebbe mai.
        val detector = HoldDetector(requiredMs = 400L, graceMs = 220L)
        detector.update(true, 0L)
        detector.update(true, 80L)
        assertFalse("buco di 80 ms", detector.update(false, 160L))
        assertTrue("il conteggio deve essere proseguito", detector.update(true, 400L))
    }

    @Test
    fun `un buco lungo azzera comunque il conteggio`() {
        val detector = HoldDetector(requiredMs = 400L, graceMs = 220L)
        detector.update(true, 0L)
        detector.update(false, 100L)
        assertFalse("oltre la tolleranza", detector.update(false, 400L))
        assertFalse("riparte da capo", detector.update(true, 500L))
        assertTrue(detector.update(true, 900L))
    }

    @Test
    fun `isHolding segue il conteggio`() {
        val detector = HoldDetector(requiredMs = 400L, graceMs = 100L)
        assertFalse(detector.isHolding)
        detector.update(true, 0L)
        assertTrue(detector.isHolding)
        detector.update(false, 500L)
        assertFalse(detector.isHolding)
    }

    @Test
    fun `un'interruzione azzera il conteggio`() {
        val detector = HoldDetector(requiredMs = 400L)
        detector.update(true, 0L)
        detector.update(false, 200L)
        assertFalse(detector.update(true, 350L))
        assertTrue(detector.update(true, 750L))
    }
}
