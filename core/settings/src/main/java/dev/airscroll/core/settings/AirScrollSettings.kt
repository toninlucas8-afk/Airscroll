package dev.airscroll.core.settings

import dev.airscroll.core.common.model.DistanceProfile
import dev.airscroll.core.common.model.HorizontalAction
import dev.airscroll.core.common.model.IndicatorCorner
import dev.airscroll.core.common.model.PerformanceMode
import dev.airscroll.core.common.model.ScrollMode
import dev.airscroll.core.common.model.SituationMode

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

    /**
     * Come la mano comanda lo scorrimento.
     *
     * Predefinito l'aggancio diretto, che e' il modello che si vuole. La
     * levetta resta scegliibile perche' e' un cambiamento molto soggettivo e
     * chi lo prova deve poter dire *quale dei due* preferisce, non solo che il
     * nuovo non gli piace.
     */
    val scrollMode: ScrollMode = ScrollMode.FOLLOW,

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
     * Dove ti trovi, come preset.
     *
     * Cucina, doccia, bagno, auto: ognuna piega gli stessi parametri che si
     * potrebbero regolare a mano, ma chiedere a qualcuno con le mani
     * nell'impasto - o al volante - di ragionare su "guadagno" e "zona neutra"
     * non ha senso. Vedi `effective`.
     */
    val situationMode: SituationMode = SituationMode.NONE,

    val horizontalAction: HorizontalAction = HorizontalAction.VOLUME,
    /** Gradini di volume al secondo alla massima escursione laterale. */
    val maxVolumeStepsPerSec: Float = 6f,

    /**
     * I due risparmi misurabili, sotto un interruttore solo.
     *
     * 1. **Cadenza vera del sensore**: si chiede alla fotocamera di acquisire
     *    piu' lentamente, invece di lasciarla andare a pieno regime e scartare
     *    i fotogrammi in eccesso - che significa pagarne il conto senza usarne
     *    il risultato.
     * 2. **Salto dei fotogrammi immobili**: in attesa la scena non cambia quasi
     *    mai, e rianalizzarla spende corrente solo per riconfermare che non
     *    succede niente.
     *
     * Acceso di partenza, ma spegnibile: e' l'unico modo perche' il misuratore
     * di consumo possa dire quanto valgono davvero, invece di doverci credere.
     * Sono sotto un interruttore solo perche' la domanda a cui serve rispondere
     * e' una sola.
     */
    val powerSaving: Boolean = true,

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
    get() {
        val mode = situationMode
        if (mode == SituationMode.NONE) return this
        return copy(
            neutralZoneScale = (neutralZoneScale * mode.neutralZone).coerceAtMost(4f),
            maxScrollSpeedPxPerSec = maxScrollSpeedPxPerSec * mode.scrollSpeed,
            sensitivity = (sensitivity * mode.sensitivity).coerceAtLeast(0.4f),
            stopHoldMs = stopHoldMs + mode.extraStopHoldMs,
            activationHoldMs = activationHoldMs + mode.extraActivationHoldMs,
            waitingWindowMs = waitingWindowMs + mode.extraWaitingWindowMs,
            horizontalAction = if (mode.volumeAllowed) horizontalAction else HorizontalAction.NONE,
        )
    }
