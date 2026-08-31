package dev.airscroll.core.power

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerReadingTest {

    private fun campioni(vararg valori: Long) = valori.map { PowerSample(it) }

    @Test
    fun `senza campioni non si inventa un numero`() {
        assertEquals(0L, averageDrawMicroAmps(emptyList()))
    }

    /** Alcuni telefoni riportano la scarica come negativa, altri come positiva. */
    @Test
    fun `il segno del sensore non cambia il risultato`() {
        val positivi = averageDrawMicroAmps(campioni(400_000, 410_000, 390_000, 400_000, 400_000))
        val negativi = averageDrawMicroAmps(campioni(-400_000, -410_000, -390_000, -400_000, -400_000))
        assertEquals(positivi, negativi)
    }

    /**
     * Il transitorio va scartato: subito dopo aver acceso la fotocamera il
     * telefono sta ancora facendo altro, e quei campioni falserebbero la media.
     */
    @Test
    fun `il transitorio iniziale non falsa la media`() {
        // Un picco iniziale enorme, poi il regime.
        val conPicco = campioni(2_000_000, 400_000, 400_000, 400_000, 400_000,
                                400_000, 400_000, 400_000, 400_000, 400_000)
        assertEquals(400_000L, averageDrawMicroAmps(conPicco))
    }

    /** Il numero che conta e' la differenza, non il valore assoluto. */
    @Test
    fun `il costo di AirScroll e' al netto del telefono acceso`() {
        val report = PowerReport(
            baselineMicroAmps = 300_000,
            waitingMicroAmps = 430_000,
            activeMicroAmps = 700_000,
        )
        assertEquals(130_000L, report.waitingCostMicroAmps)
        assertEquals(400_000L, report.activeCostMicroAmps)
    }

    /** Una misura sporca puo' dare un costo negativo: si tiene a zero. */
    @Test
    fun `un costo negativo non viene mostrato`() {
        val report = PowerReport(
            baselineMicroAmps = 500_000,
            waitingMicroAmps = 480_000,
            activeMicroAmps = 900_000,
        )
        assertEquals(0L, report.waitingCostMicroAmps)
    }

    @Test
    fun `riconosce una misura che non sta in piedi`() {
        assertTrue("senza riferimento", PowerReport(0, 100, 200).looksImplausible)
        assertTrue(
            "attivo che consuma meno della meta' del riposo",
            PowerReport(800_000, 500_000, 300_000).looksImplausible,
        )
        assertFalse(PowerReport(300_000, 430_000, 700_000).looksImplausible)
    }

    @Test
    fun `stima le ore di uso continuo`() {
        val report = PowerReport(300_000, 430_000, 750_000)   // 750 mA in attivo
        assertEquals(6.7f, report.hoursOfActiveUse(5_000), 0.2f)
        assertEquals("senza capacita' nota non si inventa", 0f, report.hoursOfActiveUse(0), 0.001f)
    }
}
