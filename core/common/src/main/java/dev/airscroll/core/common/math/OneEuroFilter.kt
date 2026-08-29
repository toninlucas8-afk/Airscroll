package dev.airscroll.core.common.math

import kotlin.math.abs

/**
 * Filtro "One Euro" (Casiez, Roussel, Vogel - CHI 2012).
 *
 * E' il compromesso migliore fra tremolio e ritardo per il tracking a mano
 * libera: quando la mano e' quasi ferma filtra molto (niente jitter), quando si
 * muove veloce filtra poco (niente lag percepito). Costa una manciata di
 * operazioni per fotogramma, quindi non pesa su CPU/batteria.
 */
class OneEuroFilter(
    private val minCutoff: Float = 1.0f,
    /**
     * Quanto il filtro si "apre" quando la mano accelera.
     *
     * Il valore va tarato sulle unita' del segnale: qui lavoriamo in coordinate
     * normalizzate 0..1, dove un movimento deciso vale circa 0,6 unita' al
     * secondo. Con un beta piccolo (i valori da mouse che si trovano in giro)
     * il ritardo su un movimento del genere supererebbe i 150 ms, cioe' piu'
     * dell'intera zona neutra: lo scorrimento sembrerebbe molle. Con 1.5 il
     * ritardo resta sotto i ~50 ms senza perdere la calma da mano ferma.
     */
    private val beta: Float = 1.5f,
    private val derivativeCutoff: Float = 1.0f,
) {
    private var initialised = false
    private var lastValue = 0f
    private var lastDerivative = 0f
    private var lastTimestampMs = 0L

    fun reset() {
        initialised = false
        lastDerivative = 0f
    }

    fun filter(value: Float, timestampMs: Long): Float {
        if (!initialised) {
            initialised = true
            lastValue = value
            lastDerivative = 0f
            lastTimestampMs = timestampMs
            return value
        }

        val dtMs = (timestampMs - lastTimestampMs).coerceAtLeast(1L)
        val dt = dtMs / 1000f
        lastTimestampMs = timestampMs

        val rawDerivative = (value - lastValue) / dt
        val derivative = lowPass(rawDerivative, lastDerivative, alpha(derivativeCutoff, dt))
        lastDerivative = derivative

        val cutoff = minCutoff + beta * abs(derivative)
        val filtered = lowPass(value, lastValue, alpha(cutoff, dt))
        lastValue = filtered
        return filtered
    }

    private fun alpha(cutoff: Float, dt: Float): Float {
        val tau = 1f / (2f * Math.PI.toFloat() * cutoff)
        return 1f / (1f + tau / dt)
    }

    private fun lowPass(value: Float, previous: Float, alpha: Float): Float =
        alpha * value + (1f - alpha) * previous
}
