package dev.airscroll.core.voice

import java.text.Normalizer
import java.util.Locale

/**
 * Da quello che si e' sentito, a cosa fare.
 *
 * Questo file e' il cuore della voce, ed e' di proposito **senza Android e
 * senza il riconoscitore**: cosi' si puo' provare con centinaia di frasi vere
 * senza avere un telefono in mano. Chi riconosce le parole e' intercambiabile;
 * chi decide cosa vogliono dire e' qui, ed e' provato.
 *
 * Il riconoscimento vocale sbaglia sempre qualcosa - "spotify" torna
 * "spotifai", "canzoni" torna "canzone" - quindi il confronto non e' mai fra
 * stringhe uguali: si normalizza (minuscolo, senza accenti, senza punteggiatura)
 * e si cercano parole chiave dentro la frase, invece di pretendere una frase
 * esatta. Chiedere alla gente di dire una formula precisa e' il modo piu' rapido
 * di far smettere di usare i comandi vocali.
 */
object VoiceParser {

    /**
     * Interpreta una frase. Restituisce null se non e' un comando.
     *
     * Null e' una risposta legittima e frequente: il microfono sente anche la
     * televisione e le conversazioni. Fare qualcosa a caso perche' si e' sentita
     * una parola nota sarebbe molto peggio che non fare niente.
     */
    fun parse(heard: String): VoiceCommand? {
        val frase = normalise(heard)
        if (frase.isBlank()) return null

        // L'ordine conta. "fai partire le mie canzoni preferite su spotify"
        // contiene sia il nome dell'app sia la richiesta di musica: la richiesta
        // piu' specifica va riconosciuta per prima, altrimenti aprirebbe
        // soltanto l'app e si fermerebbe li'.
        parseFavourites(frase)?.let { return it }
        parseGenre(frase)?.let { return it }
        parseStop(frase)?.let { return it }
        parseMedia(frase)?.let { return it }
        parseVolume(frase)?.let { return it }
        parseOpen(frase)?.let { return it }
        return null
    }

    // --- singoli comandi ---------------------------------------------------

    private fun parseFavourites(frase: String): VoiceCommand? {
        val chiedeMusica = MUSIC_WORDS.any { frase.contains(it) }
        if (!chiedeMusica) return null
        val vuolePreferite = FAVOURITE_WORDS.any { frase.contains(it) }
        if (!vuolePreferite) return null
        val app = findApp(frase) ?: AppTarget.SPOTIFY
        return VoiceCommand.PlayFavourites(app)
    }

    /**
     * Un genere dall'elenco chiuso.
     *
     * Viene dopo le preferite perche' e' meno specifico: "le mie canzoni
     * preferite" e' una richiesta precisa, "musica rock" e' una categoria.
     *
     * Serve sia una parola che parli di musica sia un genere noto. Il nome del
     * genere da solo non basta: "il concerto rock di ieri" non e' un ordine.
     */
    private fun parseGenre(frase: String): VoiceCommand? {
        val parlaDiMusica = MUSIC_WORDS.any { frase.contains(it) } ||
            PLAY_WORDS_STRONG.any { frase.contains(it) } ||
            PLAY_WORDS_WEAK.any { frase.contains(it) }
        if (!parlaDiMusica) return null
        val genere = findGenre(frase) ?: return null
        val app = findApp(frase) ?: AppTarget.SPOTIFY
        return VoiceCommand.PlayGenre(app, genere)
    }

    private fun parseStop(frase: String): VoiceCommand? {
        val fermati = STOP_WORDS.any { frase.contains(it) }
        if (!fermati) return null
        // "ferma la musica" e' un comando al lettore, non ad AirScroll: dire
        // "spengo tutto" a chi voleva solo mettere in pausa sarebbe un modo
        // sicuro di far disinstallare l'app.
        if (MEDIA_CONTEXT_WORDS.any { frase.contains(it) }) return null
        return VoiceCommand.Stop
    }

    private fun parseMedia(frase: String): VoiceCommand? = when {
        frase.containsAny(NEXT_WORDS) -> VoiceCommand.Media(MediaAction.NEXT)
        frase.containsAny(PREVIOUS_WORDS) -> VoiceCommand.Media(MediaAction.PREVIOUS)
        frase.containsAny(PAUSE_WORDS) -> VoiceCommand.Media(MediaAction.PAUSE)
        frase.containsAny(PLAY_WORDS_STRONG) -> VoiceCommand.Media(MediaAction.PLAY)
        frase.containsAny(PLAY_WORDS_WEAK) && frase.containsAny(MEDIA_CONTEXT_WORDS) ->
            VoiceCommand.Media(MediaAction.PLAY)
        else -> null
    }

    private fun parseVolume(frase: String): VoiceCommand? {
        if (!frase.contains("volume") && !frase.containsAny(VOLUME_SYNONYMS)) return null
        val su = frase.containsAny(UP_WORDS)
        val giu = frase.containsAny(DOWN_WORDS)
        if (!su && !giu) return null
        // "alza e abbassa" insieme non vuol dire niente: meglio non fare nulla
        // che scegliere a caso.
        if (su && giu) return null
        return VoiceCommand.Volume(up = su, steps = VOLUME_STEPS)
    }

    private fun parseOpen(frase: String): VoiceCommand? {
        if (!frase.containsAny(OPEN_WORDS)) return null
        val app = findApp(frase) ?: return null
        return VoiceCommand.OpenApp(app)
    }

    private fun findApp(frase: String): AppTarget? = AppTarget.entries.firstOrNull { target ->
        target.spokenNames.any { frase.contains(it) }
    }

    private fun findGenre(frase: String): Genre? = Genre.entries.firstOrNull { genere ->
        genere.spokenNames.any { frase.contains(it) }
    }

    /**
     * Riduce una frase alla sua forma confrontabile.
     *
     * Minuscolo, accenti tolti, punteggiatura via, spazi singoli. Senza questo,
     * "Apri Spotify!" e "apri spotify" sarebbero due frasi diverse, e meta' dei
     * comandi non verrebbe riconosciuta per motivi che nessuno puo' indovinare.
     */
    fun normalise(raw: String): String {
        val minuscolo = raw.lowercase(Locale.ROOT)
        val senzaAccenti = Normalizer.normalize(minuscolo, Normalizer.Form.NFD)
            .replace(ACCENTS, "")
        return senzaAccenti
            .replace(PUNCTUATION, " ")
            .replace(SPACES, " ")
            .trim()
    }

    private fun String.containsAny(words: List<String>): Boolean = words.any { contains(it) }

    // --- vocabolario -------------------------------------------------------
    //
    // Piu' varianti per la stessa cosa, di proposito: la gente non dice due
    // volte la stessa frase, e il riconoscitore aggiunge le sue storpiature.

    private val MUSIC_WORDS = listOf("musica", "canzon", "brani", "playlist", "album")
    private val FAVOURITE_WORDS = listOf("preferit", "piaciut", "salvat", "mie ", "miei ")
    private val OPEN_WORDS = listOf("apri", "apre", "lancia", "avvia", "vai su", "aprire")
    /**
     * Verbi che da soli vogliono dire "fai partire la musica".
     *
     * "riprendi" non ha altri significati plausibili davanti a un telefono.
     */
    private val PLAY_WORDS_STRONG = listOf("play", "riprendi", "riproduci", "fai partire")

    /**
     * Verbi che vogliono dire "fai partire" **solo** se si parla di musica.
     *
     * "manda un messaggio a Marco" e "metti a posto la stanza" contengono un
     * verbo di riproduzione e non c'entrano niente: e' esattamente il tipo di
     * falso positivo che fa fare all'app cose che nessuno ha chiesto, e che
     * insegna a non fidarsi dei comandi vocali.
     */
    private val PLAY_WORDS_WEAK = listOf("metti", "manda", "suona", "parti")
    private val PAUSE_WORDS = listOf("pausa", "ferma la musica", "ferma musica", "stoppa", "silenzio")
    private val NEXT_WORDS = listOf("prossima", "prossimo", "successiv", "avanti", "salta")
    private val PREVIOUS_WORDS = listOf("precedent", "indietro", "torna indietro")
    private val STOP_WORDS = listOf("spegni", "ferma tutto", "smetti", "basta", "stop airscroll")
    private val MEDIA_CONTEXT_WORDS = listOf("musica", "canzon", "brano", "video", "volume")
    private val VOLUME_SYNONYMS = listOf("audio")
    private val UP_WORDS = listOf("alza", "aumenta", "su ", "piu forte", "alto")
    private val DOWN_WORDS = listOf("abbassa", "diminuisci", "giu", "piu piano", "basso")

    /**
     * Quanti gradini di volume per un comando parlato.
     *
     * Piu' di uno: dire "alza il volume" per spostarsi di un gradino solo
     * costringerebbe a ripeterlo cinque volte, che e' esattamente la situazione
     * in cui si ha le mani occupate e non si puo'.
     */
    const val VOLUME_STEPS = 3

    private val ACCENTS = Regex("\\p{Mn}+")
    private val PUNCTUATION = Regex("[^a-z0-9 ]+")
    private val SPACES = Regex(" +")
}
