package dev.airscroll.core.settings

import dev.airscroll.core.common.model.DistanceProfile
import dev.airscroll.core.common.model.HorizontalAction
import dev.airscroll.core.common.model.IndicatorCorner
import dev.airscroll.core.common.model.PerformanceMode

/**
 * Risultato della calibrazione iniziale, quella "stile Face ID".
 *
 * Tutti i valori sono in unita' normalizzate rispetto al fotogramma, quindi
 * restano validi anche cambiando risoluzione di analisi.
 */
data class CalibrationProfile(
    val completed: Boolean = false,
    /** Dimensione apparente della mano alla distanza abituale dell'utente. */
    val referenceHandSpan: Float = DEFAULT_HAND_SPAN,
    /** Ampiezza verticale comoda misurata durante la calibrazione. */
    val verticalRange: Float = DEFAULT_VERTICAL_RANGE,
    /** Ampiezza orizzontale comoda misurata durante la calibrazione. */
    val horizontalRange: Float = DEFAULT_HORIZONTAL_RANGE,
    /** Tremolio residuo con la mano ferma: da qui esce la zona neutra. */
    val tremor: Float = DEFAULT_TREMOR,
    val calibratedAtMillis: Long = 0L,
) {
    companion object {
        const val DEFAULT_HAND_SPAN = 0.14f
        const val DEFAULT_VERTICAL_RANGE = 0.22f
        const val DEFAULT_HORIZONTAL_RANGE = 0.24f
        const val DEFAULT_TREMOR = 0.012f

        val Default = CalibrationProfile()
    }
}

/** Tutte le preferenze dell'utente. */
data class AirScrollSettings(
    val serviceEnabled: Boolean = false,
    val onboardingCompleted: Boolean = false,

    val distanceProfile: DistanceProfile = DistanceProfile.AUTO,
    val performanceMode: PerformanceMode = PerformanceMode.BALANCED,

    /** Moltiplicatore globale, 0.4 = molto calmo, 2.0 = molto nervoso. */
    val sensitivity: Float = 1.0f,
    /** Velocita' massima del dito invisibile, in pixel al secondo. */
    val maxScrollSpeedPxPerSec: Float = 2400f,
    /** Allarga o restringe la zona neutra calcolata in calibrazione. */
    val neutralZoneScale: Float = 1.0f,
    val invertScroll: Boolean = false,

    val horizontalAction: HorizontalAction = HorizontalAction.VOLUME,
    /** Gradini di volume al secondo alla massima escursione laterale. */
    val maxVolumeStepsPerSec: Float = 6f,

    val indicatorEnabled: Boolean = true,
    val indicatorCorner: IndicatorCorner = IndicatorCorner.TOP_END,
    val hapticsEnabled: Boolean = true,

    /** Durata della finestra gialla di attesa, in millisecondi. */
    val waitingWindowMs: Long = 6_000L,
    /** Quanto va tenuto il pugno chiuso per uscire. */
    val stopHoldMs: Long = 2_000L,
    /** Quanto va tenuto il pollice in su per entrare. */
    val activationHoldMs: Long = 400L,

    /** Id dei profili disattivati dall'utente. Vuoto = tutti attivi. */
    val disabledProfileIds: Set<String> = emptySet(),
    /** Package aggiunti a mano dall'utente. */
    val customPackages: Set<String> = emptySet(),

    val calibration: CalibrationProfile = CalibrationProfile.Default,
) {
    companion object {
        val Default = AirScrollSettings()
    }
}
