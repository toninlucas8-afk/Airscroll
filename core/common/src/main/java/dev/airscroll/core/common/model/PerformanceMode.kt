package dev.airscroll.core.common.model

/**
 * Compromesso fra reattivita' e consumo.
 *
 * I valori sono usati dal modulo camera per scegliere risoluzione e cadenza di
 * analisi, e dal modulo vision per il delegate (CPU o GPU).
 */
enum class PerformanceMode(
    val waitingFps: Int,
    val activeFps: Int,
    val analysisWidth: Int,
    val analysisHeight: Int,
    val preferGpu: Boolean,
) {
    /** Batteria prima di tutto: bassa risoluzione, poche analisi al secondo, solo CPU. */
    BATTERY(waitingFps = 8, activeFps = 15, analysisWidth = 320, analysisHeight = 240, preferGpu = false),

    /** Default. */
    BALANCED(waitingFps = 12, activeFps = 22, analysisWidth = 480, analysisHeight = 360, preferGpu = true),

    /** Massima fluidita' su telefoni potenti. */
    RESPONSIVE(waitingFps = 15, activeFps = 30, analysisWidth = 640, analysisHeight = 480, preferGpu = true),
}

/**
 * Come la mano comanda lo scorrimento.
 *
 * Sono due modelli diversi, non due tarature dello stesso.
 */
enum class ScrollMode {
    /**
     * La pagina sta dove metti la mano.
     *
     * Lo spostamento della mano diventa spostamento del contenuto: muovi di
     * dieci centimetri, la pagina si sposta di tanto e **si ferma li'**. E'
     * manipolazione diretta, ed e' cio' che fa leggere il gesto come "sto
     * toccando la cosa" invece di "sto pilotando qualcosa a distanza".
     *
     * Oltre una certa escursione subentra una velocita' continua, altrimenti
     * per attraversare una pagina lunga servirebbero decine di gesti. E se la
     * mano torna al centro di scatto, il contenuto prosegue e rallenta, come un
     * colpo di dito.
     */
    FOLLOW,

    /**
     * La mano decide la velocita', come una levetta analogica.
     *
     * E' il comportamento delle versioni fino alla 0.4.5. Piu' prevedibile su
     * pagine lunghissime, ma non da' mai la sensazione di toccare il contenuto:
     * la pagina non sta mai dove sta la mano.
     */
    SPEED,
}

/**
 * Le situazioni d'uso, come preset.
 *
 * Non sono decorazione: ognuna cambia gli stessi parametri che si potrebbero
 * regolare a mano, ma chiedere a qualcuno con le mani nell'impasto - o sotto
 * la doccia, o al volante - di ragionare su "guadagno" e "zona neutra" non ha
 * senso. Sotto ci sono i numeri veri; sopra c'e' una parola che descrive dove
 * ti trovi.
 *
 * I moltiplicatori **piegano** le impostazioni dell'utente, non le
 * sovrascrivono: spegnendo il preset si torna esattamente ai propri valori.
 *
 * @param neutralZone quanto allargare la zona che ignora i movimenti.
 * @param scrollSpeed quanto rallentare lo scorrimento.
 * @param sensitivity quanto smorzare la risposta.
 * @param extraStopHoldMs quanto allungare il pugno chiuso per fermare.
 * @param extraActivationHoldMs quanto allungare il pollice in su per attivare.
 * @param extraWaitingWindowMs quanto allungare la finestra gialla di attesa.
 * @param volumeAllowed se il movimento laterale puo' cambiare il volume.
 */
enum class SituationMode(
    val neutralZone: Float,
    val scrollSpeed: Float,
    val sensitivity: Float,
    val extraStopHoldMs: Long,
    val extraActivationHoldMs: Long,
    val extraWaitingWindowMs: Long,
    val volumeAllowed: Boolean = true,
) {
    /** Nessun preset: comandano i cursori dell'utente. */
    NONE(1f, 1f, 1f, 0L, 0L, 0L),

    /**
     * Mani occupate, telefono sul ripiano, distanza media.
     *
     * Zona neutra piu' larga perche' le mani sporche si muovono male, e
     * scorrimento piu' calmo perche' si segue una ricetta, non un feed.
     */
    KITCHEN(1.7f, 0.6f, 0.85f, 300L, 0L, 0L),

    /**
     * Telefono fuori dalla doccia, su un ripiano, piu' lontano.
     *
     * Tutto piu' tollerante: si e' bagnati, l'aria e' piena di vapore e la
     * mano non e' mai ferma. La finestra di attesa e' molto piu' lunga perche'
     * sotto l'acqua non si reagisce in sei secondi.
     */
    SHOWER(2.4f, 0.55f, 0.9f, 500L, 150L, 9_000L),

    /**
     * Telefono su una mensola, vicino, una mano occupata.
     *
     * Simile alla cucina ma piu' vicino e piu' calmo: qui di solito si legge.
     */
    BATHROOM(1.5f, 0.7f, 0.9f, 300L, 0L, 4_000L),

    /**
     * In auto, telefono nel supporto.
     *
     * E' il preset piu' severo, e per un motivo diverso dagli altri: qui un
     * falso positivo non e' una scomodita'. Il braccio si muove di continuo -
     * il volante, il cambio, un gesto mentre si parla - e niente di tutto
     * questo deve essere scambiato per un comando.
     *
     * Zona neutra molto larga, pollice in su piu' lungo da tenere, scorrimento
     * lento. Il volume resta, perche' e' la cosa piu' utile in auto, ma
     * richiede un movimento ampio e deliberato.
     */
    CAR(3f, 0.5f, 0.8f, 600L, 350L, 6_000L),
}

/** Come mappare lo spostamento orizzontale della mano. */
enum class HorizontalAction {
    NONE,
    VOLUME,
}

/** Dove disegnare l'indicatore di stato. */
enum class IndicatorCorner {
    /**
     * In cima, al centro, nella fascia della status bar.
     *
     * E' la posizione predefinita perche' e' dove si guarda gia': accanto
     * all'orologio e alle notifiche. Su un telefono con il foro della
     * fotocamera al centro puo' finirci sotto: in quel caso si sceglie un
     * angolo.
     */
    TOP_CENTER,
    TOP_START,
    TOP_END,
    BOTTOM_START,
    BOTTOM_END,
}
