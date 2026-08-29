package dev.airscroll.core.common.math

import kotlin.math.pow

fun Float.clamp(min: Float, max: Float): Float = when {
    this < min -> min
    this > max -> max
    else -> this
}

/**
 * Curva di risposta progressiva: piccoli spostamenti scorrono piano, spostamenti
 * ampi accelerano. [gamma] > 1 rende la parte iniziale piu' dolce.
 */
fun progressiveResponse(normalised: Float, gamma: Float): Float =
    normalised.clamp(0f, 1f).pow(gamma)

/** Media mobile esponenziale, per grandezze lente come la dimensione della mano. */
class Ema(private val alpha: Float, initial: Float = Float.NaN) {
    var value: Float = initial
        private set

    fun update(sample: Float): Float {
        value = if (value.isNaN()) sample else alpha * sample + (1f - alpha) * value
        return value
    }

    fun reset() {
        value = Float.NaN
    }
}
