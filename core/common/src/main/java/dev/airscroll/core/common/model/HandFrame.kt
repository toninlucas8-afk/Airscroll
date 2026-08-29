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
