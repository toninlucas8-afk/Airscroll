package dev.airscroll.app.voice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.getSystemService
import dev.airscroll.core.voice.AppTarget
import dev.airscroll.core.voice.Genre
import dev.airscroll.core.voice.MediaAction
import dev.airscroll.core.voice.VoiceCommand

/**
 * Esegue un comando vocale, con quello che Android mette davvero a
 * disposizione.
 *
 * Nessuna integrazione privilegiata con nessuna app: si aprono intent pubblici
 * e si mandano i tasti multimediali, gli stessi che manda un paio di cuffie con
 * il pulsante. E' meno potente di un assistente di sistema, ed e' l'unica cosa
 * che un'app senza permesso di rete e senza accordi con nessuno puo' fare
 * onestamente.
 */
class VoiceExecutor(context: Context) {

    private val appContext = context.applicationContext

    /** Restituisce true se il comando e' stato eseguito davvero. */
    fun execute(command: VoiceCommand): Boolean = when (command) {
        is VoiceCommand.OpenApp -> openApp(command.target)
        is VoiceCommand.PlayFavourites -> playFavourites(command.target)
        is VoiceCommand.PlayGenre -> playGenre(command.target, command.genre)
        is VoiceCommand.Media -> sendMediaKey(command.action)
        is VoiceCommand.Volume -> changeVolume(command.up, command.steps)
        VoiceCommand.Stop -> false // lo gestisce chi ci chiama: spegne il servizio
    }

    private fun openApp(target: AppTarget): Boolean {
        val intent = launchIntentFor(target) ?: return false
        return start(intent)
    }

    private fun launchIntentFor(target: AppTarget) = target.packages
        .firstNotNullOfOrNull { appContext.packageManager.getLaunchIntentForPackage(it) }
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Fa partire la musica preferita.
     *
     * Su Spotify si apre `spotify:collection:tracks`, che e' la raccolta dei
     * brani salvati, e subito dopo si manda il tasto play. Non esiste un intent
     * pubblico che dica "apri **e** suona": aprire la schermata la mostra e
     * basta, quindi il tasto play e' il secondo tempo necessario. E' un modo
     * indiretto, e va detto: se Spotify cambia comportamento, cambia il
     * risultato.
     *
     * Per le altre app non c'e' un equivalente sensato: si apre l'app e si
     * manda comunque il play, che sui lettori multimediali riprende l'ultima
     * cosa ascoltata.
     */
    private fun playFavourites(target: AppTarget): Boolean {
        val aperta = when (target) {
            AppTarget.SPOTIFY -> start(
                Intent(Intent.ACTION_VIEW, Uri.parse(SPOTIFY_LIKED_SONGS))
                    .setPackage(AppTarget.SPOTIFY.packages.first())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ) || openApp(target)

            else -> openApp(target)
        }
        if (!aperta) return false

        playAfterOpening()
        return true
    }

    /**
     * Fa partire un genere.
     *
     * Su Spotify si apre la ricerca di quel genere con `spotify:search:`, poi
     * si manda il comando di riproduzione. AirScroll non ha accesso alla
     * libreria di nessuno e non sa cosa ti piace: apre una ricerca, come
     * faresti tu, e preme play.
     *
     * Va detto chiaramente perche' non e' ovvio: **quello che parte dipende da
     * cosa l'app di musica mette in cima ai risultati**, non da una scelta di
     * AirScroll. Con "mista" si cerca "mix", che di solito porta alle raccolte
     * generate dall'app.
     */
    private fun playGenre(target: AppTarget, genre: Genre): Boolean {
        val aperta = when (target) {
            AppTarget.SPOTIFY -> start(
                Intent(Intent.ACTION_VIEW, Uri.parse(SPOTIFY_SEARCH + Uri.encode(genre.query)))
                    .setPackage(AppTarget.SPOTIFY.packages.first())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ) || openApp(target)

            else -> openApp(target)
        }
        if (!aperta) return false
        playAfterOpening()
        return true
    }

    /**
     * Manda il comando di riproduzione a un'app appena aperta.
     *
     * Con un attimo di attesa: il lettore deve essere pronto a riceverlo, e
     * mandarlo subito lo perde e basta.
     */
    private fun playAfterOpening() {
        Handler(Looper.getMainLooper()).postDelayed(
            { sendMediaKey(MediaAction.PLAY) },
            PLAY_DELAY_MS,
        )
    }

    /**
     * Manda un tasto multimediale al lettore attivo, qualunque sia.
     *
     * `dispatchMediaKeyEvent` e' la stessa strada che usano le cuffie con i
     * pulsanti: non serve nessun permesso speciale e non serve sapere quale app
     * sta suonando.
     */
    private fun sendMediaKey(action: MediaAction): Boolean {
        val audio = appContext.getSystemService<AudioManager>() ?: return false
        val code = when (action) {
            MediaAction.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
            MediaAction.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
            MediaAction.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            MediaAction.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        }
        val now = SystemClock.uptimeMillis()
        return runCatching {
            audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
            audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
            true
        }.getOrElse { error ->
            Log.w(TAG, "tasto multimediale rifiutato", error)
            false
        }
    }

    private fun changeVolume(up: Boolean, steps: Int): Boolean {
        val audio = appContext.getSystemService<AudioManager>() ?: return false
        val direction = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        return runCatching {
            repeat(steps.coerceIn(1, MAX_VOLUME_STEPS)) {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
            }
            true
        }.getOrElse { false }
    }

    private fun start(intent: Intent): Boolean = runCatching {
        appContext.startActivity(intent)
        true
    }.getOrElse { error ->
        Log.w(TAG, "intent rifiutato: ${intent.data ?: intent.component}", error)
        false
    }

    private companion object {
        const val TAG = "AirScroll/Voce"

        /** La raccolta dei brani salvati su Spotify. */
        const val SPOTIFY_LIKED_SONGS = "spotify:collection:tracks"

        /** La ricerca dentro Spotify. Il termine cercato va accodato. */
        const val SPOTIFY_SEARCH = "spotify:search:"

        /** Quanto si aspetta prima di mandare il play a un'app appena aperta. */
        const val PLAY_DELAY_MS = 2_500L

        const val MAX_VOLUME_STEPS = 8
    }
}
