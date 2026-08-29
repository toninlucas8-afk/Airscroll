package dev.airscroll.core.common.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OneEuroFilterTest {

    @Test
    fun `il primo campione passa inalterato`() {
        val filter = OneEuroFilter()
        assertEquals(0.42f, filter.filter(0.42f, 0L), 1e-6f)
    }

    @Test
    fun `il rumore attorno a un valore fermo viene attenuato`() {
        val filter = OneEuroFilter()
        var timestamp = 0L
        filter.filter(0.5f, timestamp)

        var maxDeviation = 0f
        val noise = listOf(0.507f, 0.494f, 0.506f, 0.493f, 0.505f, 0.495f, 0.504f, 0.496f)
        noise.forEach { sample ->
            timestamp += 33
            val filtered = filter.filter(sample, timestamp)
            maxDeviation = maxOf(maxDeviation, abs(filtered - 0.5f))
        }

        // Il rumore in ingresso arriva a 0.007: in uscita deve restare molto sotto.
        assertTrue("deviazione residua $maxDeviation", maxDeviation < 0.004f)
    }

    @Test
    fun `un movimento vero viene seguito senza restare indietro`() {
        val filter = OneEuroFilter()
        var timestamp = 0L
        filter.filter(0.5f, timestamp)

        var value = 0.5f
        repeat(15) {
            timestamp += 33
            value += 0.02f
            filter.filter(value, timestamp)
        }
        val filtered = filter.filter(value, timestamp + 33)
        // Tre fotogrammi di ritardo (0,06 unita' a questa velocita') e' il massimo
        // che si puo' accettare prima che lo scorrimento sembri molle.
        assertTrue("il filtro si e' fermato a $filtered contro $value", filtered > value - 0.06f)
    }
}
