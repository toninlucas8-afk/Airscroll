package dev.airscroll.app.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * Ascolta una frase, sul telefono, senza rete.
 *
 * Usa il riconoscitore **su dispositivo** di Android
 * (`createOnDeviceSpeechRecognizer`), non quello normale: la differenza non e'
 * un dettaglio di prestazioni, e' l'intera ragione per cui questa funzione puo'
 * esistere in AirScroll. Il riconoscitore normale manda l'audio ai server di
 * Google; questo lavora dentro il telefono, con un modello scaricato dal
 * sistema.
 *
 * AirScroll non dichiara il permesso INTERNET: e' un fatto controllabile
 * nell'APK, e vale anche per la voce.
 *
 * Il limite, detto chiaro: serve Android 12 o piu' recente, e serve che il
 * sistema abbia gia' il suo modello vocale per la lingua. Dove non c'e', la
 * voce resta spenta e l'app lo dice, invece di ripiegare in silenzio su un
 * riconoscimento che passa dalla rete.
 */
class VoiceListener(context: Context) {

    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    /** La voce puo' funzionare su questo telefono? */
    fun isAvailable(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
            } else {
                // Su Android 12 la domanda non si puo' fare: si prova a creare
                // il riconoscitore, che e' l'unica risposta onesta disponibile.
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
                    .also { it.destroy() }
                true
            }
        }.getOrDefault(false)
    }

    /**
     * Apre il microfono per una frase sola.
     *
     * @param onHeard riceve il testo riconosciuto, o null se non si e' capito.
     */
    fun listenOnce(onHeard: (String?) -> Unit) {
        if (listening) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            onHeard(null)
            return
        }

        val creato = runCatching {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
        }.getOrElse { error ->
            Log.w(TAG, "riconoscitore su dispositivo non disponibile", error)
            onHeard(null)
            return
        }

        recognizer = creato
        listening = true
        creato.setRecognitionListener(Callback(onHeard))
        runCatching { creato.startListening(intent()) }.onFailure { error ->
            Log.w(TAG, "startListening rifiutato", error)
            finish()
            onHeard(null)
        }
    }

    /** Chiude il microfono adesso. */
    fun stop() {
        runCatching { recognizer?.cancel() }
        finish()
    }

    private fun finish() {
        listening = false
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        )
        // La lingua del telefono: chi ha il telefono in italiano parla italiano.
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_RESULTS)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
    }

    /**
     * Le risposte del riconoscitore.
     *
     * Ogni strada che finisce - risultato, errore, silenzio - deve chiamare
     * `onHeard` una volta sola: chi ascolta deve poter chiudere la sessione
     * sapendo che non arrivera' altro.
     */
    private inner class Callback(private val onHeard: (String?) -> Unit) : RecognitionListener {

        private var answered = false

        private fun answer(text: String?) {
            if (answered) return
            answered = true
            finish()
            onHeard(text)
        }

        override fun onResults(results: Bundle?) {
            val frasi = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            answer(frasi?.firstOrNull())
        }

        override fun onError(error: Int) {
            // Niente di allarmante: il caso piu' comune e' che non si sia
            // parlato affatto.
            answer(null)
        }

        override fun onEndOfSpeech() = Unit
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private companion object {
        const val TAG = "AirScroll/Voce"
        const val MAX_RESULTS = 3
    }
}
