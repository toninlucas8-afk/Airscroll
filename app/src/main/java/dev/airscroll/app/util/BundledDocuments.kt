package dev.airscroll.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import dev.airscroll.app.R
import java.io.File

/**
 * Il manuale e l'informativa privacy, che viaggiano dentro l'APK.
 *
 * Stanno nell'app e non solo su internet per un motivo che non e' comodita':
 * AirScroll non puo' raggiungere la rete, e un documento che spiega proprio
 * questo sarebbe una contraddizione se per leggerlo servisse una connessione.
 * Chi installa l'app ha con se' tutto quello che le riguarda.
 *
 * Un asset dentro l'APK non ha un percorso che si possa passare a un'altra app,
 * quindi va prima copiato in cache e poi consegnato tramite `FileProvider`.
 */
enum class BundledDocument(val assetName: String, val titleRes: Int) {
    MANUAL("AirScroll-manuale.pdf", R.string.document_manual),
    PRIVACY("AirScroll-privacy.pdf", R.string.document_privacy),
}

object BundledDocuments {

    private const val TAG = "AirScroll/Docs"
    private const val AUTHORITY_SUFFIX = ".recordings"
    private const val FOLDER = "documenti"

    /**
     * Apre il documento con il lettore PDF del telefono.
     *
     * Se non ce n'e' nessuno si ripiega sulla condivisione, cosi' l'utente puo'
     * comunque mandarlo dove vuole invece di trovarsi davanti a un nulla di
     * fatto.
     */
    fun open(context: Context, document: BundledDocument) {
        val uri = runCatching { copyToCache(context, document) }
            .getOrElse { error ->
                Log.w(TAG, "Documento non estratto", error)
                Toast.makeText(context, R.string.document_open_failed, Toast.LENGTH_LONG).show()
                return
            }

        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(view)
            return
        } catch (missingViewer: ActivityNotFoundException) {
            Log.i(TAG, "Nessun lettore PDF: si ripiega sulla condivisione", missingViewer)
        }

        val share = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(
                Intent.createChooser(share, context.getString(document.titleRes))
            )
        }.onFailure {
            Toast.makeText(context, R.string.document_open_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun copyToCache(context: Context, document: BundledDocument): android.net.Uri {
        val folder = File(context.cacheDir, FOLDER).apply { mkdirs() }
        val target = File(folder, document.assetName)

        // Si riscrive ogni volta: e' qualche centinaio di kilobyte, e cosi' un
        // aggiornamento dell'app non lascia in giro la versione vecchia.
        context.assets.open(document.assetName).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }

        return FileProvider.getUriForFile(
            context,
            context.packageName + AUTHORITY_SUFFIX,
            target,
        )
    }
}
