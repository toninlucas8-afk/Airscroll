package dev.airscroll.core.voice

/**
 * Decide quando aprire il microfono.
 *
 * E' la parte piu' delicata di tutta la voce, e non per motivi tecnici: un
 * microfono che si apre quando vuole lui e' esattamente cio' che la gente teme
 * di un'app che chiede fotocamera e accessibilita'. Quindi la regola e' una
 * sola, dichiarata, e verificabile leggendo questo file:
 *
 * **il microfono si apre solo dopo un gesto fatto apposta, e si chiude da solo
 * dopo pochi secondi.**
 *
 * Niente parola di attivazione, che vorrebbe dire ascoltare sempre. Niente
 * apertura automatica quando "sembra" che serva. Il gesto scelto e' la V con
 * due dita: non e' nessuno dei gesti che comandano lo scorrimento, quindi non
 * puo' partire per sbaglio mentre si legge.
 */
class VoiceGate(
    private val holdMs: Long = DEFAULT_HOLD_MS,
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
) {

    /** Cosa sta facendo il microfono. */
    enum class State {
        /** Chiuso. */
        CLOSED,

        /** Aperto: sta ascoltando. */
        OPEN,
    }

    var state: State = State.CLOSED
        private set

    private var heldSince = NEVER
    private var openedAt = NEVER
    private var closedAt = NEVER

    /**
     * Un fotogramma di gesto.
     *
     * @param victory true se in questo fotogramma si vede la V.
     * @return true nell'istante esatto in cui il microfono va aperto.
     */
    fun onFrame(victory: Boolean, nowMs: Long): Boolean {
        if (state == State.OPEN) {
            // Aperto: l'unica cosa che conta e' quando scade.
            if (nowMs - openedAt >= windowMs) close(nowMs)
            return false
        }

        // Dopo una sessione c'e' una pausa: senza, la V ancora alzata mentre si
        // finisce di parlare riaprirebbe subito il microfono, e da fuori
        // sembrerebbe che non si chiuda mai.
        if (closedAt != NEVER && nowMs - closedAt < cooldownMs) {
            heldSince = NEVER
            return false
        }

        if (!victory) {
            heldSince = NEVER
            return false
        }

        if (heldSince == NEVER) {
            heldSince = nowMs
            return false
        }

        if (nowMs - heldSince < holdMs) return false

        heldSince = NEVER
        state = State.OPEN
        openedAt = nowMs
        return true
    }

    /** Quanto manca alla chiusura, da 1 a 0. Zero se il microfono e' chiuso. */
    fun remaining(nowMs: Long): Float {
        if (state != State.OPEN) return 0f
        val left = windowMs - (nowMs - openedAt)
        return (left.toFloat() / windowMs).coerceIn(0f, 1f)
    }

    /** Chiude subito: si usa quando il comando e' stato capito. */
    fun close(nowMs: Long) {
        state = State.CLOSED
        openedAt = NEVER
        closedAt = nowMs
        heldSince = NEVER
    }

    /** Azzera tutto, compresa la pausa. Si usa quando la voce viene spenta. */
    fun reset() {
        state = State.CLOSED
        heldSince = NEVER
        openedAt = NEVER
        closedAt = NEVER
    }

    companion object {
        private const val NEVER = -1L

        /** Quanto va tenuta la V perche' conti. */
        const val DEFAULT_HOLD_MS = 700L

        /** Quanto resta aperto il microfono. */
        const val DEFAULT_WINDOW_MS = 6_000L

        /** Pausa fra una sessione e la successiva. */
        const val DEFAULT_COOLDOWN_MS = 1_500L
    }
}
