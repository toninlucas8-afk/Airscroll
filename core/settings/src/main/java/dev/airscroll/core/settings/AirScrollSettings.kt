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
    /**
     * Ampiezza verticale comoda: la media fra [reachUp] e [reachDown].
     *
     * Resta come riepilogo, per le schermate che mostrano un numero solo.
     */
    val verticalRange: Float = DEFAULT_VERTICAL_RANGE,
    /** Ampiezza orizzontale comoda: la media fra [reachLeft] e [reachRight]. */
    val horizontalRange: Float = DEFAULT_HORIZONTAL_RANGE,

    // --- Portata per direzione ---------------------------------------------
    //
    // Un numero solo non bastava, e non e' un dettaglio: alla prova su telefono
    // uno dei due versi dello scorrimento sembrava non funzionare affatto.
    // Nessuno arriva alla stessa distanza in tutte le direzioni - il braccio
    // sale piu' facilmente di quanto scenda, e verso il lato del braccio che
    // gesticola si arriva molto piu' lontano che dall'altro - e forzare una
    // media fra due versi diversi rende uno dei due lento e l'altro nervoso.
    //
    // Il cerchio da completare in calibrazione serve a misurarli tutti e
    // quattro davvero, invece di stimarne uno.

    /** Quanto in alto arriva la mano, dal centro. */
    val reachUp: Float = DEFAULT_VERTICAL_RANGE,
    /** Quanto in basso arriva la mano, dal centro. */
    val reachDown: Float = DEFAULT_VERTICAL_RANGE,
    /** Quanto a sinistra arriva la mano, dal centro. */
    val reachLeft: Float = DEFAULT_HORIZONTAL_RANGE,
    /** Quanto a destra arriva la mano, dal centro. */
    val reachRight: Float = DEFAULT_HORIZONTAL_RANGE,

    /** Tremolio residuo con la mano ferma: da qui esce la zona neutra. */
    val tremor: Float = DEFAULT_TREMOR,
    val calibratedAtMillis: Long = 0L,
) {

    /**
     * Ricalcola i due riepiloghi dalle quattro portate misurate, cosi' non
     * possono raccontare qualcosa di diverso dai dati veri.
     */
    fun withDerivedRanges(): CalibrationProfile = copy(
        verticalRange = (reachUp + reachDown) / 2f,
        horizontalRange = (reachLeft + reachRight) / 2f,
    )

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

    /**
     * Preset "mani occupate": allarga la zona neutra e rallenta lo scorrimento.
     *
     * E' un interruttore solo perche' chiedere a qualcuno con le mani nell'impasto
     * di ragionare su "guadagno" e "zona neutra" non ha senso. Sotto, muove i
     * parametri veri (vedi `effective`).
     */
    val kitchenMode: Boolean = false,

    val horizontalAction: HorizontalAction = HorizontalAction.VOLUME,
    /** Gradini di volume al secondo alla massima escursione laterale. */
    val maxVolumeStepsPerSec: Float = 6f,

    val indicatorEnabled: Boolean = true,
    val indicatorCorner: IndicatorCorner = IndicatorCorner.TOP_CENTER,
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

/**
 * Impostazioni con i preset applicati.
 *
 * Il motore consuma sempre questa versione, mai quella grezza: cosi' i preset
 * non sovrascrivono i cursori dell'utente, li piegano soltanto finche' sono
 * attivi.
 */
val AirScrollSettings.effective: AirScrollSettings
    get() = if (!kitchenMode) this else copy(
        neutralZoneScale = (neutralZoneScale * 1.7f).coerceAtMost(3f),
        maxScrollSpeedPxPerSec = maxScrollSpeedPxPerSec * 0.6f,
        sensitivity = (sensitivity * 0.85f).coerceAtLeast(0.4f),
        stopHoldMs = stopHoldMs + 300L,
    )
