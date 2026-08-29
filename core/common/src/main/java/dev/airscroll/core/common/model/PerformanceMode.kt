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
    BATTERY(waitingFps = 6, activeFps = 15, analysisWidth = 320, analysisHeight = 240, preferGpu = false),

    /** Default. */
    BALANCED(waitingFps = 8, activeFps = 22, analysisWidth = 480, analysisHeight = 360, preferGpu = true),

    /** Massima fluidita' su telefoni potenti. */
    RESPONSIVE(waitingFps = 12, activeFps = 30, analysisWidth = 640, analysisHeight = 480, preferGpu = true),
}

/** Come mappare lo spostamento orizzontale della mano. */
enum class HorizontalAction {
    NONE,
    VOLUME,
}

/** Angolo in cui disegnare l'indicatore di stato. */
enum class IndicatorCorner {
    TOP_START,
    TOP_END,
    BOTTOM_START,
    BOTTOM_END,
}
