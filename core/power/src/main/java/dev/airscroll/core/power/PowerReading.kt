package dev.airscroll.core.power

import kotlin.math.abs

/**
 * Una fase della misura di consumo, con i suoi campioni.
 *
 * Tenuta separata da Android apposta: la parte difficile non e' leggere il
 * sensore, e' capire cosa significhi il numero che restituisce. Qui quella
 * parte si puo' provare davvero.
 */
data class PowerSample(val microAmps: Long)

/**
 * Media robusta dei campioni di corrente.
 *
 * Si scarta il primo quinto: subito dopo aver cambiato stato il telefono sta
 * ancora facendo altro - accendere la fotocamera, allocare il modello - e quei
 * campioni raccontano il transitorio, non il regime.
 *
 * Il segno viene ignorato. Alcuni telefoni riportano la corrente in scarica
 * come negativa, altri come positiva, e non esiste un modo affidabile di
 * saperlo in anticipo: quello che serve e' il valore assoluto, e soprattutto la
 * **differenza** fra due fasi misurate nello stesso modo.
 */
fun averageDrawMicroAmps(samples: List<PowerSample>): Long {
    if (samples.isEmpty()) return 0L
    val skip = samples.size / 5
    val useful = samples.drop(skip).takeIf { it.isNotEmpty() } ?: samples
    return useful.sumOf { abs(it.microAmps) } / useful.size
}

/**
 * Il risultato della misura guidata.
 *
 * Il numero che conta non e' il consumo assoluto - lo schermo acceso pesa piu'
 * di tutto il resto messo insieme, e da solo direbbe poco - ma **quanto
 * AirScroll aggiunge** rispetto allo stesso telefono, con lo stesso schermo
 * acceso, che non sta facendo niente.
 */
data class PowerReport(
    val baselineMicroAmps: Long,
    val waitingMicroAmps: Long,
    val activeMicroAmps: Long,
) {
    /** Quanto costa lo stato di attesa, al netto del telefono acceso. */
    val waitingCostMicroAmps: Long get() = (waitingMicroAmps - baselineMicroAmps).coerceAtLeast(0L)

    /** Quanto costa lo stato attivo, al netto del telefono acceso. */
    val activeCostMicroAmps: Long get() = (activeMicroAmps - baselineMicroAmps).coerceAtLeast(0L)

    /**
     * Stima di quante ore durerebbe una batteria usando AirScroll di continuo
     * in questo stato, escluso tutto il resto.
     *
     * E' volutamente grezza e va letta come ordine di grandezza.
     */
    fun hoursOfActiveUse(batteryCapacityMilliAmpHours: Int): Float {
        val totalMilliAmps = activeMicroAmps / 1000f
        if (totalMilliAmps <= 1f || batteryCapacityMilliAmpHours <= 0) return 0f
        return batteryCapacityMilliAmpHours / totalMilliAmps
    }

    val looksImplausible: Boolean
        get() = baselineMicroAmps == 0L || activeMicroAmps == 0L ||
            activeMicroAmps < baselineMicroAmps / 2
}
