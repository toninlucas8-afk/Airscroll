package dev.airscroll.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import dev.airscroll.app.R
import dev.airscroll.core.settings.AirScrollSettings
import dev.airscroll.core.settings.ProfileTransfer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Porta il profilo dentro e fuori dal telefono.
 *
 * La scrittura e la lettura del contenuto stanno in `ProfileTransfer`, che e'
 * provato; qui c'e' solo il contorno Android - un file in cache, un `Uri` per
 * consegnarlo a un'altra app, e la lettura di un file scelto dall'utente.
 */
object ProfileFiles {

    private const val TAG = "AirScroll/Profilo"
    private const val AUTHORITY_SUFFIX = ".recordings"
    private const val FOLDER = "profilo"

    /** Il tipo MIME con cui viene consegnato: testo, perche' testo e'. */
    const val MIME = "text/plain"

    /**
     * Scrive il profilo e restituisce l'intent per condividerlo.
     *
     * Il nome del file contiene la data: chi ne salva due a distanza di mesi
     * deve poter capire quale sia quale senza aprirli.
     */
    fun shareIntent(context: Context, settings: AirScrollSettings): Intent? {
        val uri = runCatching { write(context, settings) }.getOrElse { error ->
            Log.w(TAG, "profilo non scritto", error)
            return null
        }
        return Intent(Intent.ACTION_SEND).apply {
            type = MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.profile_file_subject))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun write(context: Context, settings: AirScrollSettings): Uri {
        val directory = File(context.cacheDir, FOLDER).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val file = File(directory, "airscroll-profilo-$stamp.txt")
        file.writeText(ProfileTransfer.encode(settings))
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}$AUTHORITY_SUFFIX",
            file,
        )
    }

    /**
     * Legge un profilo scelto dall'utente.
     *
     * Restituisce null se il file non e' leggibile o non e' un profilo: in
     * entrambi i casi non si tocca niente. Un limite di dimensione c'e' perche'
     * il selettore di file lascia scegliere qualunque cosa, e un video da un
     * giga non va letto in memoria per scoprire che non e' un profilo.
     */
    fun read(context: Context, uri: Uri, current: AirScrollSettings): AirScrollSettings? {
        val testo = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                // Lettura limitata scritta a mano: `readNBytes` e' di Java 11 e
                // su Android 8 non c'e'. Il limite serve perche' il selettore
                // di file lascia scegliere qualunque cosa, e un video da un giga
                // non va letto in memoria per scoprire che non e' un profilo.
                val buffer = ByteArray(MAX_BYTES)
                var letti = 0
                while (letti < MAX_BYTES) {
                    val n = stream.read(buffer, letti, MAX_BYTES - letti)
                    if (n <= 0) break
                    letti += n
                }
                String(buffer, 0, letti, Charsets.UTF_8)
            }
        }.getOrElse { error ->
            Log.w(TAG, "profilo non letto", error)
            null
        } ?: return null

        return ProfileTransfer.decode(testo, current)
    }

    /** Un profilo sta in poche centinaia di byte: questo e' gia' larghissimo. */
    private const val MAX_BYTES = 64 * 1024
}
