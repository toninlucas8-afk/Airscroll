package dev.airscroll.core.gesture

import dev.airscroll.core.common.model.EngineState
import dev.airscroll.core.common.model.HandSignal

/**
 * La scatola nera, scritta e riletta.
 *
 * CSV per le stesse ragioni delle registrazioni del laboratorio: chi la manda
 * puo' aprirla e vedere con i propri occhi che dentro ci sono solo numeri, e
 * chi la riceve puo' rileggerla senza tirarsi dietro niente.
 *
 * La rilettura sta qui, insieme alla scrittura, perche' e' cio' che rende la
 * registrazione **utile invece che solo condivisibile**: un test puo' ricaricare
 * un file vero e rigiocarlo nel motore, e da quel momento una modifica alle
 * soglie non e' piu' una scommessa - si vede subito se sui movimenti veri
 * cambia qualcosa, e cosa.
 */
object FlightRecordFormat {

    const val FORMAT_VERSION = 1

    private const val HEADER = "# airscroll-volo v"
    private const val COLUMNS = "t,present,gesture,score,palmX,palmY,span,state,velocity"

    fun encode(
        samples: List<FlightRecorder.Sample>,
        device: String,
        android: String,
        note: String = "",
    ): String = buildString {
        appendLine("$HEADER$FORMAT_VERSION")
        appendLine("# nessuna immagine, solo coordinate normalizzate 0..1")
        appendLine("# device=$device")
        appendLine("# android=$android")
        if (note.isNotBlank()) appendLine("# nota=${note.replace('\n', ' ')}")
        appendLine("# campioni=${samples.size}")
        appendLine(COLUMNS)

        samples.forEach { s ->
            append(s.timestampMs).append(',')
            append(if (s.present) 1 else 0).append(',')
            append(s.signal.name).append(',')
            append(round(s.confidence)).append(',')
            append(round(s.palmX)).append(',')
            append(round(s.palmY)).append(',')
            append(round(s.handSpan)).append(',')
            append(s.state.name).append(',')
            append(round(s.scrollVelocity, 1))
            appendLine()
        }
    }

    /**
     * Rilegge una registrazione.
     *
     * Le righe che non si capiscono vengono saltate invece di far fallire tutto:
     * una registrazione arrivata per messaggio puo' avere una riga tagliata in
     * fondo, e buttare via due minuti di dati veri per l'ultima riga sarebbe
     * assurdo.
     */
    fun decode(text: String): List<FlightRecorder.Sample> {
        val righe = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()
        if (righe.isEmpty()) return emptyList()

        val corpo = if (righe.first().startsWith("t,")) righe.drop(1) else righe
        return corpo.mapNotNull { riga ->
            val campi = riga.split(',')
            if (campi.size < 9) return@mapNotNull null
            runCatching {
                FlightRecorder.Sample(
                    timestampMs = campi[0].toLong(),
                    present = campi[1] == "1",
                    signal = enumValueOf<HandSignal>(campi[2]),
                    confidence = campi[3].toFloat(),
                    palmX = campi[4].toFloat(),
                    palmY = campi[5].toFloat(),
                    handSpan = campi[6].toFloat(),
                    state = enumValueOf<EngineState>(campi[7]),
                    scrollVelocity = campi[8].toFloat(),
                )
            }.getOrNull()
        }
    }

    private fun round(value: Float, decimals: Int = 4): String {
        if (value.isNaN() || value.isInfinite()) return "0"
        val factor = when (decimals) {
            1 -> 10f
            else -> 10_000f
        }
        val rounded = kotlin.math.round(value * factor) / factor
        return rounded.toString()
    }
}
