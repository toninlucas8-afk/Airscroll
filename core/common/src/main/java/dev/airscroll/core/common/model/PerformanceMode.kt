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
