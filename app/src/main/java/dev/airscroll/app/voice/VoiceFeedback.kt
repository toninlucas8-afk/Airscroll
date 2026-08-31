package dev.airscroll.app.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import dev.airscroll.app.R
import dev.airscroll.core.voice.Genre
import dev.airscroll.core.voice.MediaAction
import dev.airscroll.core.voice.VoiceCommand

/**
 * Dice ad alta voce cosa ha capito.
 *
 * Senza questo, la voce era muta in un modo che la rende inutilizzabile: parli,
 * e o succede qualcosa o non succede niente. Se non succede niente non sai
 * quale delle tre cose e' andata storta - non ti ha sentito, ha sentito male,
 * oppure ha capito ma l'app non c'era. Con le mani bagnate sotto la doccia e'
 * la differenza fra usarlo e smettere.
 *
 * Quando non capisce, **mostra cosa ha sentito**. E' il pezzo che vale di piu':
 * vedere "non ho capito: «apri spoti fai»" spiega in un colpo solo perche' non
 * ha funzionato, e dice anche quale parola aggiungere al vocabolario.
 *
 * Si usa un Toast e non un riquadro disegnato sopra le app: compare anche
 * dentro le altre app, non chiede nessun permesso in piu' e non puo' coprire
 * niente di importante.
 */
class VoiceFeedback(context: Context) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    /** Il microfono si e' appena aperto. */
    fun listening() = show(appContext.getString(R.string.voice_listening))

    /** Ha capito, ed ecco cosa sta facendo. */
    fun understood(command: VoiceCommand) = show(describe(command))

    /**
     * Ha sentito qualcosa che non e' un comando.
     *
     * Il testo sentito viene mostrato per intero: e' l'unico modo per capire se
     * il problema e' la pronuncia, il rumore, o una frase che al vocabolario
     * manca davvero.
     */
    fun notUnderstood(heard: String?) {
        val testo = heard?.takeIf { it.isNotBlank() }
        show(
            if (testo == null) {
                appContext.getString(R.string.voice_heard_nothing)
            } else {
                appContext.getString(R.string.voice_not_understood, testo)
            }
        )
    }

    /** Ha capito, ma l'azione non e' riuscita: quasi sempre l'app non c'e'. */
    fun failed(command: VoiceCommand) {
        val nome = (command as? VoiceCommand.OpenApp)?.target
            ?: (command as? VoiceCommand.PlayFavourites)?.target
            ?: (command as? VoiceCommand.PlayGenre)?.target
        show(
            if (nome != null) {
                appContext.getString(R.string.voice_app_missing, capitalise(nome.spokenNames.first()))
            } else {
                appContext.getString(R.string.voice_command_failed)
            }
        )
    }

    private fun describe(command: VoiceCommand): String = when (command) {
        is VoiceCommand.OpenApp ->
            appContext.getString(R.string.voice_heard_open, capitalise(command.target.spokenNames.first()))

        is VoiceCommand.PlayFavourites ->
            appContext.getString(R.string.voice_heard_favourites)

        is VoiceCommand.PlayGenre ->
            appContext.getString(R.string.voice_heard_genre, genreLabel(command.genre))

        is VoiceCommand.Media -> appContext.getString(
            when (command.action) {
                MediaAction.PLAY -> R.string.voice_heard_play
                MediaAction.PAUSE -> R.string.voice_heard_pause
                MediaAction.NEXT -> R.string.voice_heard_next
                MediaAction.PREVIOUS -> R.string.voice_heard_previous
            }
        )

        is VoiceCommand.Volume -> appContext.getString(
            if (command.up) R.string.voice_heard_volume_up else R.string.voice_heard_volume_down
        )

        VoiceCommand.Stop -> appContext.getString(R.string.voice_heard_stop)
    }

    private fun genreLabel(genre: Genre) = capitalise(genre.spokenNames.first())

    private fun capitalise(word: String) = word.replaceFirstChar { it.uppercase() }

    private fun show(text: String) {
        main.post { Toast.makeText(appContext, text, Toast.LENGTH_SHORT).show() }
    }
}
