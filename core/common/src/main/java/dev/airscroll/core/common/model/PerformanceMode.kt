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
