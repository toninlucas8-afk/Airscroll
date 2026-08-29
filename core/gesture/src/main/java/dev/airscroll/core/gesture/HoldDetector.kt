package dev.airscroll.core.gesture

/**
 * Rileva "questa condizione e' vera senza interruzioni da almeno N ms".
 *
 * Serve sia per il pollice in su (attivazione) sia per il pugno chiuso
 * (uscita). Espone anche [progress] cosi' l'indicatore puo' mostrare che il
 * gesto e' stato capito e sta per scattare.
 */
class HoldDetector(var requiredMs: Long) {

    private var startedAt = 0L
    private var holding = false
    private var fired = false

    /** @return true una sola volta, nell'istante in cui la soglia viene superata. */
    fun update(condition: Boolean, nowMs: Long): Boolean {
        if (!condition) {
            reset()
            return false
        }
        if (!holding) {
            holding = true
            startedAt = nowMs
        }
        if (fired) return false
        if (nowMs - startedAt >= requiredMs) {
            fired = true
            return true
        }
        return false
    }

    fun progress(nowMs: Long): Float {
        if (!holding || requiredMs <= 0L) return 0f
        return ((nowMs - startedAt).toFloat() / requiredMs).coerceIn(0f, 1f)
    }

    fun reset() {
        holding = false
        startedAt = 0L
        fired = false
    }
}
