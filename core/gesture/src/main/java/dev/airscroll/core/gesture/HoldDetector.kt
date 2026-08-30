package dev.airscroll.core.gesture

/**
 * Rileva "questa condizione e' vera senza interruzioni da almeno N ms".
 *
 * Con una tolleranza, pero': il riconoscimento visivo non e' continuo. Fra due
 * fotogrammi il punteggio del gesto puo' scendere sotto soglia per un attimo -
 * la mano ruota appena, un dito copre il pollice, la luce cambia - e senza
 * tolleranza ogni singolo buco azzererebbe il conteggio. A 8 fotogrammi al
 * secondo, tenere il pollice in su per 400 ms significa tre fotogrammi: se uno
 * si perde, l'attivazione non scatta mai e all'utente sembra che l'app non
 * funzioni.
 *
 * [graceMs] e' quanto a lungo la condizione puo' restare falsa senza che il
 * conteggio riparta da zero.
 */
class HoldDetector(
    var requiredMs: Long,
    var graceMs: Long = 0L,
) {

    private var startedAt = 0L
    private var lastTrueAt = 0L
    private var holding = false
    private var fired = false

    /** true mentre il conteggio e' in corso: serve per applicare l'isteresi. */
    val isHolding: Boolean get() = holding

    /** @return true una sola volta, nell'istante in cui la soglia viene superata. */
    fun update(condition: Boolean, nowMs: Long): Boolean {
        if (condition) {
            if (!holding) {
                holding = true
                startedAt = nowMs
                fired = false
            }
            lastTrueAt = nowMs
        } else {
            if (!holding) return false
            if (nowMs - lastTrueAt > graceMs) {
                reset()
                return false
            }
            // Dentro la tolleranza il conteggio prosegue: un buco breve non
            // significa che l'utente abbia cambiato idea.
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
        fired = false
        startedAt = 0L
        lastTrueAt = 0L
    }
}
