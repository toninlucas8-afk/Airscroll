package dev.airscroll.core.gesture

import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

/**
 * Il cerchio da completare della calibrazione, senza la parte grafica.
 *
 * L'idea e' quella di Face ID: non si chiede all'utente di regolare dei numeri
 * astratti, gli si chiede di fare il movimento e si misura. Il cerchio non e'
 * decorazione - e' cio' che lo spinge a esplorare *tutte* le direzioni, invece
 * di muovere la mano dove gli viene comodo e lasciare il resto a una stima.
 *
 * Qui dentro non c'e' niente di Android: si puo' provare sul serio con dei
 * test, ed e' l'unico modo per sapere che misura quello che dice di misurare.
 */
class ReachMap(private val sectors: Int = DEFAULT_SECTORS) {

    private val reached = FloatArray(sectors)
    private val filled = BooleanArray(sectors)

    private var centerX = 0.5f
    private var centerY = 0.5f

    /** Quante direzioni sono state completate. */
    val filledCount: Int get() = filled.count { it }

    /** Da 0 a 1: quanto del cerchio e' acceso. */
    val progress: Float get() = filledCount.toFloat() / sectors

    val isComplete: Boolean get() = filledCount == sectors

    /** Stato di ogni spicchio, per disegnarli. */
    fun sectorStates(): List<Boolean> = filled.toList()

    /** Il punto di riposo, misurato nella fase "tieni la mano ferma". */
    fun centerOn(x: Float, y: Float) {
        centerX = x
        centerY = y
        reached.fill(0f)
        filled.fill(false)
    }

    fun reset() {
        centerX = 0.5f
        centerY = 0.5f
        reached.fill(0f)
        filled.fill(false)
    }

    /**
     * Registra dove si trova la mano adesso.
     *
     * @return la posizione dentro il cerchio, per il puntino che segue la mano.
     */
    fun accept(x: Float, y: Float): Position {
        val dx = x - centerX
        // Verso l'alto dell'inquadratura y diminuisce: qui si ragiona con l'asse
        // rivolto in su, come lo vede l'utente.
        val dy = centerY - y
        val radius = hypot(dx, dy)
        val index = sectorOf(dx, dy)

        if (radius > reached[index]) reached[index] = radius

        // Uno spicchio si accende se la mano ci e' arrivata abbastanza lontano,
        // **oppure** se e' arrivata al bordo di cio' che la fotocamera vede: in
        // quella direzione non puo' andare oltre, e pretenderlo lascerebbe
        // l'utente bloccato davanti a un cerchio che non si chiude mai.
        val atFrameEdge = x <= FRAME_EDGE || x >= 1f - FRAME_EDGE ||
            y <= FRAME_EDGE || y >= 1f - FRAME_EDGE
        if (radius >= TARGET_RADIUS || (atFrameEdge && radius >= MIN_EDGE_RADIUS)) {
            filled[index] = true
        }

        return Position(dx = dx, dy = dy, radius = radius, sector = index)
    }

    /**
     * Le quattro portate misurate.
     *
     * Non si prende il raggio dello spicchio piu' vicino all'asse: si proietta
     * ogni spicchio sull'asse che interessa e si tiene il massimo. Cosi' una
     * mano che e' arrivata lontano "in alto a destra" conta anche come portata
     * verso l'alto, che e' quello che poi fa davvero.
     */
    fun toReach(): Reach {
        var up = 0f
        var down = 0f
        var left = 0f
        var right = 0f
        for (index in 0 until sectors) {
            val radius = reached[index]
            if (radius <= 0f) continue
            val angle = angleOf(index)
            val projectedX = radius * kotlin.math.cos(angle)
            val projectedY = radius * kotlin.math.sin(angle)
            if (projectedY > 0f) up = max(up, projectedY) else down = max(down, -projectedY)
            if (projectedX > 0f) right = max(right, projectedX) else left = max(left, -projectedX)
        }
        return Reach(up = up, down = down, left = left, right = right)
    }

    private fun sectorOf(dx: Float, dy: Float): Int {
        val angle = atan2(dy, dx)
        val turns = (angle / TWO_PI + 1f) % 1f
        return (turns * sectors).toInt().coerceIn(0, sectors - 1)
    }

    /** Angolo al centro dello spicchio, in radianti. */
    private fun angleOf(index: Int): Float = (index + 0.5f) / sectors * TWO_PI

    data class Position(val dx: Float, val dy: Float, val radius: Float, val sector: Int)

    data class Reach(val up: Float, val down: Float, val left: Float, val right: Float)

    companion object {
        const val DEFAULT_SECTORS = 12

        /** Quanto lontano deve arrivare la mano perche' uno spicchio si accenda. */
        const val TARGET_RADIUS = 0.11f

        /**
         * Al bordo dell'inquadratura basta molto meno.
         *
         * Serve a chi ha poca mobilita', e a chi tiene il telefono vicino: senza
         * questa via d'uscita il cerchio potrebbe non chiudersi mai, e una
         * calibrazione che non si puo' finire e' peggio di nessuna calibrazione.
         */
        const val MIN_EDGE_RADIUS = 0.05f

        const val FRAME_EDGE = 0.08f

        private const val TWO_PI = (2.0 * Math.PI).toFloat()
    }
}
