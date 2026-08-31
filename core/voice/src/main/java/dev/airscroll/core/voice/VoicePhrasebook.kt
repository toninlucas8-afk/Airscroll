package dev.airscroll.core.voice

/**
 * L'elenco completo di cio' che si puo' dire.
 *
 * Esiste per una ragione precisa: **il vocabolario dev'essere una promessa
 * verificabile, non una mia parola**. Chi accende un microfono ha diritto di
 * sapere esattamente cosa quell'app ascolta e cosa no, e "fidati, capisce solo
 * questi comandi" non e' una risposta.
 *
 * Da qui nascono sia la tabella dentro l'app sia quella nel manuale, cosi' non
 * possono raccontare cose diverse. E c'e' un test che prende **ogni** frase
 * scritta qui e verifica che [VoiceParser] la interpreti davvero come dichiarato:
 * se qualcuno cambia il vocabolario senza aggiornare la tabella, la build si
 * ferma. La documentazione non puo' mentire perche' non le e' permesso.
 *
 * Attenzione a cosa questo elenco **non** e': non e' una lista di formule da
 * recitare. Pretendere la frase esatta e' il modo piu' rapido di far smettere
 * di usare i comandi vocali - basta un "senti, aprimi Spotify" e non succede
 * niente. Qui c'e' il modo consigliato di dirlo; il riconoscimento resta
 * tollerante sulle parole intorno.
 */
object VoicePhrasebook {

    /** Come sono raggruppati i comandi quando si mostrano. */
    enum class Group {
        SPOTIFY,
        ALTRE_APP,
        LETTORE,
        VOLUME,
        AIRSCROLL,
    }

    /**
     * Un comando, con il modo consigliato di dirlo.
     *
     * @param canonical la frase da mettere in tabella.
     * @param alternatives altri modi che funzionano allo stesso modo. Non sono
     *   tutti - il riconoscimento e' tollerante - ma sono garantiti dai test.
     */
    data class Phrase(
        val group: Group,
        val canonical: String,
        val command: VoiceCommand,
        val alternatives: List<String> = emptyList(),
    )

    val entries: List<Phrase> = buildList {
        // --- Spotify --------------------------------------------------------
        add(
            Phrase(
                group = Group.SPOTIFY,
                canonical = "apri Spotify",
                command = VoiceCommand.OpenApp(AppTarget.SPOTIFY),
                alternatives = listOf("avvia Spotify", "lancia Spotify"),
            )
        )
        add(
            Phrase(
                group = Group.SPOTIFY,
                canonical = "riproduci i miei brani preferiti",
                command = VoiceCommand.PlayFavourites(AppTarget.SPOTIFY),
                alternatives = listOf(
                    "fai partire le mie canzoni preferite",
                    "metti le mie canzoni preferite",
                    "apri Spotify e riproduci i miei brani preferiti",
                ),
            )
        )
        // Un comando per genere: sono nove parole note, e l'elenco chiuso e'
        // proprio il punto - un riconoscitore offline su testo libero sbaglia
        // molto piu' spesso di uno che deve scegliere fra nove possibilita'.
        Genre.entries.forEach { genere ->
            add(
                Phrase(
                    group = Group.SPOTIFY,
                    canonical = "riproduci musica ${genere.spokenNames.first()}",
                    command = VoiceCommand.PlayGenre(AppTarget.SPOTIFY, genere),
                    alternatives = listOf(
                        "metti musica ${genere.spokenNames.first()}",
                        "riproduci musica ${genere.spokenNames.first()} da Spotify",
                    ),
                )
            )
        }

        // --- le altre app ---------------------------------------------------
        AppTarget.entries.filter { it != AppTarget.SPOTIFY }.forEach { app ->
            add(
                Phrase(
                    group = Group.ALTRE_APP,
                    canonical = "apri ${app.spokenNames.first()}",
                    command = VoiceCommand.OpenApp(app),
                    alternatives = listOf("avvia ${app.spokenNames.first()}"),
                )
            )
        }

        // --- il lettore in corso, qualunque sia -----------------------------
        add(
            Phrase(
                group = Group.LETTORE,
                canonical = "prossima canzone",
                command = VoiceCommand.Media(MediaAction.NEXT),
                alternatives = listOf("canzone successiva", "salta"),
            )
        )
        add(
            Phrase(
                group = Group.LETTORE,
                canonical = "canzone precedente",
                command = VoiceCommand.Media(MediaAction.PREVIOUS),
                alternatives = listOf("torna indietro"),
            )
        )
        add(
            Phrase(
                group = Group.LETTORE,
                canonical = "metti in pausa",
                command = VoiceCommand.Media(MediaAction.PAUSE),
                alternatives = listOf("ferma la musica", "pausa"),
            )
        )
        add(
            Phrase(
                group = Group.LETTORE,
                canonical = "riprendi",
                command = VoiceCommand.Media(MediaAction.PLAY),
                alternatives = listOf("play"),
            )
        )

        // --- volume ---------------------------------------------------------
        add(
            Phrase(
                group = Group.VOLUME,
                canonical = "alza il volume",
                command = VoiceCommand.Volume(up = true, steps = VoiceParser.VOLUME_STEPS),
                alternatives = listOf("aumenta il volume"),
            )
        )
        add(
            Phrase(
                group = Group.VOLUME,
                canonical = "abbassa il volume",
                command = VoiceCommand.Volume(up = false, steps = VoiceParser.VOLUME_STEPS),
                alternatives = listOf("diminuisci il volume"),
            )
        )

        // --- AirScroll stesso -----------------------------------------------
        add(
            Phrase(
                group = Group.AIRSCROLL,
                canonical = "spegni",
                command = VoiceCommand.Stop,
                alternatives = listOf("ferma tutto", "basta"),
            )
        )
    }

    /** I comandi di un gruppo, nell'ordine in cui vanno mostrati. */
    fun of(group: Group): List<Phrase> = entries.filter { it.group == group }

    /**
     * Tutte le frasi garantite: le canoniche e le alternative.
     *
     * Serve al test che verifica che l'elenco dica la verita'.
     */
    fun everySpokenForm(): List<Pair<String, VoiceCommand>> = entries.flatMap { phrase ->
        (listOf(phrase.canonical) + phrase.alternatives).map { it to phrase.command }
    }
}
