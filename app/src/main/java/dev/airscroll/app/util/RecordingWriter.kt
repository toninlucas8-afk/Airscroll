package dev.airscroll.app.util

import android.content.Context
import android.os.Build
import dev.airscroll.core.common.model.HandFrame
import dev.airscroll.core.common.model.HandLandmarks
import dev.airscroll.core.vision.TrackerStats
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Scrive una registrazione del laboratorio in CSV.
 *
 * CSV e non JSON per tre motivi concreti: chi registra puo' aprire il file e
 * vedere con i propri occhi che contiene solo numeri, l'analisi si fa con due
 * righe di Python, e i test possono rileggerlo senza tirarsi dietro una
 * libreria di parsing.
 *
 * Nel file non finisce **nessuna immagine**: solo le coordinate dei 21 punti
 * della mano, il gesto riconosciuto e il suo punteggio.
 */
object RecordingWriter {

    const val FORMAT_VERSION = 1

    fun buildCsv(
        takes: List<Take>,
        stats: TrackerStats,
        analysisWidth: Int,
        analysisHeight: Int,
    ): String = buildString {
        appendLine("# airscroll-lab v$FORMAT_VERSION")
        appendLine("# nessuna immagine, solo coordinate normalizzate 0..1")
        appendLine("# device=${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("# android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}")
        appendLine("# delegate=${if (stats.usingGpu) "GPU" else "CPU"}")
        appendLine("# analysis=${analysisWidth}x$analysisHeight")
        appendLine("# submitted=${stats.submitted} results=${stats.results} dropped=${stats.droppedBusy} stalls=${stats.stalls}")
        appendLine("# recorded=${timestamp()}")

        append("take,expected,frame,t,present,gesture,score,palmX,palmY,span")
        for (index in 0 until HandLandmarks.COUNT) {
            append(",lm${index}x,lm${index}y,lm${index}z")
        }
        appendLine()

        takes.forEachIndexed { takeIndex, take ->
            take.frames.forEachIndexed { frameIndex, frame ->
                append(takeIndex).append(',')
                append(take.expected).append(',')
                append(frameIndex).append(',')
                append(frame.timestampMs).append(',')
                append(if (frame.present) 1 else 0).append(',')
                append(frame.signal.name).append(',')
                append(round(frame.signalConfidence)).append(',')
                append(round(frame.palmX)).append(',')
                append(round(frame.palmY)).append(',')
                append(round(frame.handSpan))
                val points = frame.landmarks
                for (index in 0 until HandLandmarks.COUNT) {
                    val point = points?.getOrNull(index)
                    append(',').append(round(point?.x))
                    append(',').append(round(point?.y))
                    append(',').append(round(point?.z))
                }
                appendLine()
            }
        }
    }

    fun write(context: Context, csv: String): File {
        val directory = File(context.getExternalFilesDir(null), "lab").apply { mkdirs() }
        val file = File(directory, "airscroll-lab-${timestamp()}.csv")
        file.writeText(csv)
        return file
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    /** Quattro decimali: sotto quella soglia e' rumore del modello, non segnale. */
    private fun round(value: Float?): String =
        if (value == null) "" else String.format(Locale.US, "%.4f", value)

    /** Una presa: il gesto chiesto all'utente e i fotogrammi registrati. */
    data class Take(
        val expected: String,
        val frames: MutableList<HandFrame> = mutableListOf(),
    )
}
