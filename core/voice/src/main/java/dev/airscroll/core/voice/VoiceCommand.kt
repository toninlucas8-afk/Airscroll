package dev.airscroll.core.voice

/**
 * Cosa AirScroll sa fare quando glielo si dice a voce.
 *
 * Deliberatamente pochi comandi, e tutti verificabili: ognuno corrisponde a
 * un'azione che il telefono sa fare davvero, senza rete e senza indovinare
 * niente. Un assistente che accetta qualunque frase e ne capisce meta' e'
 * peggio di uno che ne accetta venti e le azzecca tutte - perche' quello che
 * fallisce a caso non ci si fida piu' a usarlo con le mani bagnate.
 */
sealed interface VoiceCommand {

    /** Apre un'app, per nome parlato. */
    data class OpenApp(val target: AppTarget) : VoiceCommand

    /** Fa partire la musica preferita dell'utente. */
    data class PlayFavourites(val target: AppTarget) : VoiceCommand

    /**
     * Fa partire un genere, scelto da un elenco chiuso.
     *
     * Chiuso e non libero di proposito: "metti qualcosa di quel gruppo che mi
     * piaceva" non e' un comando, e un riconoscitore offline su testo libero
     * sbaglia molto piu' spesso di uno che deve scegliere fra nove parole note.
     */
    data class PlayGenre(val target: AppTarget, val genre: Genre) : VoiceCommand

    /** Comandi del lettore in corso, qualunque esso sia. */
    data class Media(val action: MediaAction) : VoiceCommand

    /** Volume su e giu', a gradini. */
    data class Volume(val up: Boolean, val steps: Int = 1) : VoiceCommand

    /** Ferma AirScroll. */
    data object Stop : VoiceCommand
}

/**
 * Le app che AirScroll sa aprire per nome.
 *
 * Un elenco chiuso, non una ricerca fra tutte le app installate: il
 * riconoscimento vocale offline lavora molto meglio su un vocabolario piccolo,
 * e "apri quella cosa lì" non e' un comando.
 */
enum class AppTarget(
    /** Come lo si chiama parlando, in minuscolo e senza accenti. */
    val spokenNames: List<String>,
    /** I package da provare, nell'ordine. */
    val packages: List<String>,
) {
    SPOTIFY(
        spokenNames = listOf("spotify", "spotifai", "spotifi"),
        packages = listOf("com.spotify.music"),
    ),
    YOUTUBE(
        spokenNames = listOf("youtube", "you tube", "iutub", "iutube"),
        packages = listOf("com.google.android.youtube", "com.google.android.apps.youtube.music"),
    ),
    INSTAGRAM(
        spokenNames = listOf("instagram", "instagra"),
        packages = listOf("com.instagram.android"),
    ),
    WHATSAPP(
        spokenNames = listOf("whatsapp", "watsap", "whatsap"),
        packages = listOf("com.whatsapp"),
    ),
    BROWSER(
        spokenNames = listOf("browser", "internet", "chrome", "brauser"),
        packages = listOf("com.android.chrome", "org.mozilla.firefox"),
    ),
    MAPS(
        spokenNames = listOf("mappe", "maps", "google maps", "navigatore"),
        packages = listOf("com.google.android.apps.maps"),
    ),
}

/**
 * I generi che si possono chiedere a voce.
 *
 * Ognuno diventa una ricerca dentro l'app di musica. AirScroll non ha accesso
 * alla libreria di nessuno e non sa cosa ti piace: apre la ricerca di quel
 * genere e manda il comando di riproduzione. E' quello che si puo' fare
 * onestamente senza chiedere di collegare un account.
 */
enum class Genre(
    /** Come lo si chiama parlando. */
    val spokenNames: List<String>,
    /** Cosa si cerca dentro l'app. */
    val query: String,
) {
    MISTA(listOf("mista", "misto", "mix", "casuale", "a caso", "varia"), "mix"),
    JAZZ(listOf("jazz"), "jazz"),
    ROCK(listOf("rock"), "rock"),
    POP(listOf("pop"), "pop"),
    RAP(listOf("rap", "hip hop", "trap"), "rap"),
    CLASSICA(listOf("classica", "classico"), "musica classica"),
    ELETTRONICA(listOf("elettronica", "techno", "house", "dance"), "elettronica"),
    ITALIANA(listOf("italiana", "italiano"), "musica italiana"),
    RELAX(listOf("relax", "rilassante", "chill", "tranquilla"), "relax"),
}

enum class MediaAction { PLAY, PAUSE, NEXT, PREVIOUS }
