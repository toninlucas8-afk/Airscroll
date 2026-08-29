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
    fun `un'interruzione azzera il conteggio`() {
        val detector = HoldDetector(requiredMs = 400L)
        detector.update(true, 0L)
        detector.update(false, 200L)
        assertFalse(detector.update(true, 350L))
        assertTrue(detector.update(true, 750L))
    }
}
