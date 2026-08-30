package dev.airscroll.core.common.model

/**
 * Risultato di un singolo fotogramma analizzato.
 *
 * Le coordinate sono normalizzate 0..1 e gia' specchiate come in un selfie:
 * [palmX] cresce verso destra *dal punto di vista dell'utente*, [palmY] cresce
 * verso il basso.
 */
data class HandFrame(
    val timestampMs: Long,
    val present: Boolean,
    val signal: HandSignal,
    val signalConfidence: Float,
    val palmX: Float,
    val palmY: Float,
    /**
     * Distanza normalizzata polso -> nocca del medio. E' inversamente
     * proporzionale alla distanza fisica della mano dalla fotocamera, quindi la
     * usiamo per il profilo distanza automatico.
     */
    val handSpan: Float,
    /**
     * I 21 punti grezzi, presenti solo quando la registrazione e' attiva.
     *
     * Fuori dal laboratorio resta null: allocare una lista per ogni fotogramma
     * a venti fotogrammi al secondo sarebbe spazzatura per il garbage
     * collector, in cambio di niente.
     */
    val landmarks: List<Landmark>? = null,
) {
    companion object {
        fun absent(timestampMs: Long) = HandFrame(
            timestampMs = timestampMs,
            present = false,
            signal = HandSignal.NONE,
            signalConfidence = 0f,
            palmX = 0.5f,
            palmY = 0.5f,
            handSpan = 0f,
        )
    }
}
