package dev.airscroll.core.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class FollowControllerTest {

    private val span = FollowController.DEFAULT_SPAN_PX
    private val maxSpeed = 2_400f
    private val dt = 1f / 25f

    /** Simula dei fotogrammi e restituisce quanto contenuto si e' mosso. */
    private fun FollowController.run(
        excursion: Float,
        engaged: Boolean,
        frames: Int,
        startMs: Long = 0L,
    ): Pair<Float, Long> {
        var moved = 0f
        var now = startMs
        repeat(frames) {
            now += (dt * 1000).toLong()
            moved += update(excursion, engaged, dt, span, maxSpeed, now) * dt
        }
        return moved to now
    }

    /**
     * La proprieta' che definisce l'aggancio diretto: la mano si ferma, il
     * contenuto si ferma. Col vecchio modello a velocita' avrebbe continuato a
     * scorrere all'infinito.
     */
    @Test
    fun `tenendo la mano ferma a meta' escursione il contenuto si ferma`() {
        val controller = FollowController()
        // Mezzo secondo per raggiungere la posizione...
        val (primo, tempo) = controller.run(0.4f, engaged = true, frames = 12)
        // ...e un altro secondo tenendo fermo.
        val (secondo, _) = controller.run(0.4f, engaged = true, frames = 25, startMs = tempo)

        assertTrue("il primo tratto deve muovere qualcosa: $primo", abs(primo) > 200f)
        assertTrue(
            "tenendo fermo non deve continuare a scorrere: ancora $secondo px",
            abs(secondo) < abs(primo) * 0.12f,
        )
    }

    /** Il contenuto arriva dove dice la mano, non a caso. */
    @Test
    fun `l'escursione piena della zona diretta vale tutto lo span`() {
        val controller = FollowController()
        val (moved, _) = controller.run(FollowController.FOLLOW_LIMIT, engaged = true, frames = 40)
        assertEquals("spostamento atteso circa lo span", span, abs(moved), span * 0.08f)
    }

    /** Oltre la zona diretta si entra nella spinta continua. */
    @Test
    fun `spingendo oltre il limite lo scorrimento non si ferma piu'`() {
        val controller = FollowController()
        val (primo, tempo) = controller.run(1f, engaged = true, frames = 25)
        val (secondo, _) = controller.run(1f, engaged = true, frames = 25, startMs = tempo)
        assertTrue("deve continuare a scorrere: $secondo px nel secondo tratto", abs(secondo) > 400f)
        assertTrue("e non deve rallentare: $primo poi $secondo", abs(secondo) > abs(primo) * 0.5f)
    }

    /**
     * Il riaggancio: tornare a riposo non deve riportare indietro la pagina.
     * E' l'equivalente aereo di staccare il dito.
     */
    @Test
    fun `tornare a riposo non riporta indietro il contenuto`() {
        val controller = FollowController()
        val (andata, tempo) = controller.run(0.4f, engaged = true, frames = 20)
        // Rientro lento: non e' un colpo di dito, quindi niente inerzia.
        val (ritorno, tempo2) = controller.run(0f, engaged = false, frames = 30, startMs = tempo + 2_000)
        val (dopo, _) = controller.run(0f, engaged = false, frames = 25, startMs = tempo2)

        assertTrue("l'andata deve muovere: $andata", abs(andata) > 200f)
        assertTrue("il ritorno non deve tornare indietro: $ritorno px", abs(ritorno) < 40f)
        assertEquals("e poi deve stare fermo", 0f, dopo, 1f)
    }

    /** Due bracciate consecutive sommano, invece di annullarsi. */
    @Test
    fun `due bracciate spostano il doppio`() {
        val controller = FollowController()
        val (prima, t1) = controller.run(0.4f, engaged = true, frames = 20)
        val (_, t2) = controller.run(0f, engaged = false, frames = 20, startMs = t1 + 2_000)
        val (seconda, _) = controller.run(0.4f, engaged = true, frames = 20, startMs = t2)

        assertTrue("la prima muove: $prima", abs(prima) > 200f)
        assertEquals(
            "la seconda bracciata deve valere quanto la prima",
            abs(prima), abs(seconda), abs(prima) * 0.2f,
        )
        assertEquals("e nello stesso verso", 1f, sign(prima) * sign(seconda), 0.001f)
    }

    /** Il colpo di dito: rientro di scatto, il contenuto prosegue e rallenta. */
    @Test
    fun `un rientro di scatto lascia correre il contenuto`() {
        val controller = FollowController()
        val (_, tempo) = controller.run(0.8f, engaged = true, frames = 10)
        val (coast, _) = controller.run(0f, engaged = false, frames = 25, startMs = tempo)
        assertTrue("dopo il colpo di dito deve proseguire: $coast px", abs(coast) > 100f)
    }

    /** Ma un rientro lento no: quello e' un braccio che si riposa. */
    @Test
    fun `un rientro lento non lascia correre niente`() {
        val controller = FollowController()
        // Bracciata lunga: oltre la soglia del colpo di dito.
        val (_, tempo) = controller.run(0.8f, engaged = true, frames = 40)
        val (coast, _) = controller.run(0f, engaged = false, frames = 25, startMs = tempo)
        assertTrue("non deve proseguire: $coast px", abs(coast) < 40f)
    }

    /** L'inerzia deve esaurirsi, non strisciare per sempre. */
    @Test
    fun `l'inerzia si esaurisce`() {
        val controller = FollowController()
        val (_, tempo) = controller.run(0.8f, engaged = true, frames = 10)
        val (_, tempo2) = controller.run(0f, engaged = false, frames = 50, startMs = tempo)
        val (tardi, _) = controller.run(0f, engaged = false, frames = 25, startMs = tempo2)
        assertEquals("dopo due secondi deve essere ferma", 0f, tardi, 1f)
    }

    private fun sign(value: Float) = if (value > 0f) 1f else if (value < 0f) -1f else 0f
}
