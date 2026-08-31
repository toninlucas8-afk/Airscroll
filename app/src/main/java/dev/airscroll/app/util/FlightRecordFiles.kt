package dev.airscroll.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import dev.airscroll.app.R
import dev.airscroll.core.gesture.FlightRecordFormat
import dev.airscroll.core.gesture.FlightRecorder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manda gli ultimi due minuti.
 *
 * La scatola nera vive in memoria e non tocca il disco: qui, e solo qui, quello
 * che c'e' dentro diventa un file - perche' qualcuno ha premuto un pulsante.
 *
 * Il file resta in cache e viene consegnato all'app che l'utente sceglie. Non
 * parte niente da solo: AirScroll non ha nemmeno il permesso di rete.
 */
object FlightRecordFiles {

    private const val TAG = "AirScroll/Volo"
    private const val AUTHORITY_SUFFIX = ".recordings"
    private const val FOLDER = "volo"

    const val MIME = "text/csv"

    /**
     * Scrive la registrazione e restituisce l'intent per condividerla.
     *
     * Null se non c'e' ancora niente da mandare: e' il caso di chi preme il
     * pulsante prima di aver usato l'app, e va detto invece di consegnare un
     * file vuoto.
     */
    fun shareIntent(context: Context, recorder: FlightRecorder): Intent? {
        val samples = recorder.snapshot()
        if (samples.isEmpty()) return null

        val uri = runCatching { write(context, recorder) }.getOrElse { error ->
            Log.w(TAG, "registrazione non scritta", error)
            return null
        }
        val secondi = recorder.spanSeconds().toInt()
        return Intent(Intent.ACTION_SEND).apply {
            type = MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_SUBJECT,
                context.getString(R.string.flight_file_subject, secondi),
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun write(context: Context, recorder: FlightRecorder): Uri {
        val directory = File(context.cacheDir, FOLDER).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(directory, "airscroll-volo-$stamp.csv")
        file.writeText(
            FlightRecordFormat.encode(
                samples = recorder.snapshot(),
                device = "${Build.MANUFACTURER} ${Build.MODEL}",
                android = "${Build.VERSION.RELEASE} (sdk ${Build.VERSION.SDK_INT})",
            )
        )
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}$AUTHORITY_SUFFIX",
            file,
        )
    }
}
