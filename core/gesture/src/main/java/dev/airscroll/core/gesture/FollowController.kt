package dev.airscroll.core.gesture

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign

/**
 * L'aggancio diretto: la pagina sta dove metti la mano.
 *
 * Fino alla 0.4.5 la mano era una levetta analogica - posizione della mano,
 * velocita' di scorrimento - e per quanto la si tarasse bene, non dava mai la
 * sensazione di toccare il contenuto: la pagina non stava mai dove stava la
 * mano.
 *
 * Qui lo spostamento della mano diventa spostamento del contenuto. Ma la sola
 * manipolazione diretta non basterebbe, e vale la pena dire perche':
 *
 * - **Il dito invisibile ha una corsa finita.** Puo' percorrere al massimo la
 *   banda utile dello schermo. Una pagina lunga richiederebbe decine di gesti.
 * - **In aria non esiste "staccare il dito".** Con un dito vero si trascina, si
 *   stacca, si riappoggia piu' in alto e si trascina ancora. Una mano a mezz'aria
 *   che torna indietro riporterebbe indietro anche il contenuto.
 *
 * Da qui i tre pezzi, che insieme fanno il gesto completo:
 *
 * 1. **Zona diretta** (fino a [FOLLOW_LIMIT] dell'escursione): il contenuto
 *    insegue la posizione della mano. E' qui che vive la precisione.
 * 2. **Zona di spinta** (oltre): l'offset diretto resta al massimo e si aggiunge
 *    una velocita' continua, che cresce con quanto spingi oltre. Serve ad
 *    attraversare le pagine lunghe senza cambiare gesto.
 * 3. **Riaggancio nella zona neutra**: quando la mano torna a riposo, il punto
 *    di riferimento si sposta sul contenuto attuale. E' l'equivalente aereo di
 *    staccare il dito: tornare indietro con la mano **non** riporta indietro la
 *    pagina, e si puo' fare un'altra bracciata.
 *
 * Piu' l'inerzia: se la mano rientra nella zona neutra **di scatto**, il
 * contenuto prosegue e rallenta, come un colpo di dito. Se rientra piano, si
 * ferma dove sta. La differenza fra le due e' l'unica cosa che distingue un
 * gesto voluto da un braccio che si riposa.
 */
class FollowController {

    /** Contenuto gia' comandato, in pixel. Serve solo come riferimento relativo. */
    private var commanded = 0f

    /** Da dove si misura lo spostamento diretto: il "dito appoggiato". */
    private var reference = 0f

    private var inertiaVelocity = 0f
    private var wasEngaged = false
    private var lastExcursion = 0f
    private var leftNeutralAt = 0L
    private var lastVelocity = 0f

    fun reset() {
        commanded = 0f
        reference = 0f
        inertiaVelocity = 0f
        wasEngaged = false
        lastExcursion = 0f
        leftNeutralAt = 0L
        lastVelocity = 0f
    }

    /**
     * @param excursion escursione con segno, da -1 a 1, gia' normalizzata sulla
     *   portata misurata in quella direzione. Zero significa mano a riposo.
     * @param engaged false quando la mano e' dentro la zona neutra.
     * @param dtSeconds tempo dal fotogramma precedente.
     * @param spanPx quanto contenuto vale un'escursione piena nella zona diretta.
     * @param maxSpeed velocita' massima della zona di spinta, in pixel al secondo.
     * @return la velocita' da comandare, in pixel al secondo.
     */
    fun update(
        excursion: Float,
        engaged: Boolean,
        dtSeconds: Float,
        spanPx: Float,
        maxSpeed: Float,
        nowMs: Long,
    ): Float {
        if (dtSeconds <= 0f) return lastVelocity

        if (!engaged) {
            val velocity = releaseAndCoast(dtSeconds, nowMs)
            commanded += velocity * dtSeconds
            reference = commanded
            wasEngaged = false
            lastExcursion = 0f
            lastVelocity = velocity
            return velocity
        }

        if (!wasEngaged) {
            // Prima bracciata: si "appoggia il dito" dove sta il contenuto ora.
            reference = commanded
            inertiaVelocity = 0f
            leftNeutralAt = nowMs
            wasEngaged = true
        }

        val clamped = excursion.coerceIn(-1f, 1f)

        // La spinta fa scivolare il punto di riferimento, non si somma alla
        // velocita'.
        //
        // Sommarla era il primo tentativo, e un test lo ha bocciato: con
        // l'ancora ferma, l'inseguimento tirava indietro esattamente quanto la
        // spinta tirava avanti, e il contenuto si assestava fermo a un offset
        // costante invece di continuare a scorrere. Spingere al massimo non
        // faceva scorrere niente.
        //
        // Facendo scivolare l'ancora, la spinta e' esattamente quello che un
        // dito fa quando lo trascini fino al bordo di una lista: il punto di
        // presa continua a spostarsi, e il contenuto lo segue.
        val over = (abs(clamped) - FOLLOW_LIMIT) / (1f - FOLLOW_LIMIT)
        if (over > 0f) {
            reference += sign(clamped) * maxSpeed * over * over * dtSeconds
        }

        val direct = (clamped / FOLLOW_LIMIT).coerceIn(-1f, 1f) * spanPx
        val target = reference + direct

        // Inseguimento proporzionale: il contenuto raggiunge la posizione della
        // mano in una costante di tempo breve. E' cio' che rende il movimento
        // continuo invece che a scatti, senza far vibrare la pagina.
        var velocity = (target - commanded) / TRACK_TAU_SECONDS
        velocity = velocity.coerceIn(-maxSpeed * OVERSHOOT_HEADROOM, maxSpeed * OVERSHOOT_HEADROOM)
        commanded += velocity * dtSeconds
        lastExcursion = clamped
        lastVelocity = velocity
        return velocity
    }

    /**
     * La mano e' rientrata a riposo: si decide se lasciar correre o fermarsi.
     *
     * Un rientro **di scatto** e' un colpo di dito e merita l'inerzia. Un rientro
     * lento e' un braccio che si riposa, e li' fermarsi dove si sta e' l'unica
     * cosa che non sorprende.
     */
    private fun releaseAndCoast(dtSeconds: Float, nowMs: Long): Float {
        if (wasEngaged) {
            val flick = abs(lastExcursion) >= FLICK_MIN_EXCURSION &&
                nowMs - leftNeutralAt <= FLICK_MAX_STROKE_MS
            inertiaVelocity = if (flick) lastVelocity * FLICK_KEEP else 0f
        }
        if (abs(inertiaVelocity) < INERTIA_FLOOR) {
            inertiaVelocity = 0f
            return 0f
        }
        inertiaVelocity *= exp(-dtSeconds / DECAY_TAU_SECONDS)
        return inertiaVelocity
    }

    companion object {
        /** Frazione dell'escursione in cui il contenuto insegue la mano. */
        const val FOLLOW_LIMIT = 0.55f

        /**
         * Quanto contenuto vale un'escursione piena nella zona diretta.
         *
         * Circa due terzi di uno schermo alto: abbastanza perche' il gesto
         * sposti qualcosa di visibile, poco abbastanza perche' resti preciso.
         */
        const val DEFAULT_SPAN_PX = 1_400f

        /** Con quanta prontezza il contenuto raggiunge la mano. */
        const val TRACK_TAU_SECONDS = 0.085f

        /** Margine sopra la velocita' massima, per non troncare l'inseguimento. */
        const val OVERSHOOT_HEADROOM = 1.4f

        /** Quanto dura l'inerzia: dopo questo tempo e' scesa a un terzo. */
        const val DECAY_TAU_SECONDS = 0.32f

        /** Sotto questa velocita' l'inerzia si spegne invece di strisciare. */
        const val INERTIA_FLOOR = 60f

        /** Quanta velocita' sopravvive al rilascio. */
        const val FLICK_KEEP = 0.85f

        /** Sotto questa escursione il rientro non e' un colpo di dito. */
        const val FLICK_MIN_EXCURSION = 0.35f

        /** Oltre questa durata la bracciata e' un movimento lento, non un flick. */
        const val FLICK_MAX_STROKE_MS = 900L
    }
}
