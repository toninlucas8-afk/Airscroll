package dev.airscroll.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Le frasi vere, non quelle di comodo.
 *
 * Questo file e' l'unico posto in cui si puo' verificare la voce senza avere un
 * telefono e un microfono: ogni caso qui dentro e' una frase che qualcuno dira'
 * davvero, storpiature del riconoscitore comprese.
 */
class VoiceParserTest {

    @Test
    fun `apri spotify`() {
        assertEquals(
            VoiceCommand.OpenApp(AppTarget.SPOTIFY),
            VoiceParser.parse("apri spotify"),
        )
    }

    @Test
    fun `la frase vera dell'utente, per intero`() {
        // "airscroll apri Spotify e fai partire le mie canzoni preferite":
        // contiene sia il nome dell'app sia la richiesta di musica. Vince la
        // richiesta piu' specifica, altrimenti aprirebbe l'app e si fermerebbe
        // li' - facendo meta' di quello che era stato chiesto.
        assertEquals(
            VoiceCommand.PlayFavourites(AppTarget.SPOTIFY),
            VoiceParser.parse("airscroll apri spotify e fai partire le mie canzoni preferite"),
        )
    }

    @Test
    fun `le preferite senza dire quale app finiscono su spotify`() {
        assertEquals(
            VoiceCommand.PlayFavourites(AppTarget.SPOTIFY),
            VoiceParser.parse("metti le mie canzoni preferite"),
        )
    }

    @Test
    fun `maiuscole, accenti e punteggiatura non cambiano niente`() {
        // Chi parla non detta la punteggiatura, ma il riconoscitore la mette.
        assertEquals(
            VoiceCommand.OpenApp(AppTarget.YOUTUBE),
            VoiceParser.parse("Apri YouTube!"),
        )
        assertEquals(
            VoiceCommand.OpenApp(AppTarget.MAPS),
            VoiceParser.parse("apri il navigatore, perché sì"),
        )
    }

    @Test
    fun `le storpiature del riconoscitore vengono accettate`() {
        // Nessun riconoscitore offline scrive "spotify" tutte le volte.
        assertEquals(
            VoiceCommand.OpenApp(AppTarget.SPOTIFY),
            VoiceParser.parse("apri spotifai"),
        )
        assertEquals(
            VoiceCommand.OpenApp(AppTarget.YOUTUBE),
            VoiceParser.parse("apri iutub"),
        )
    }

    @Test
    fun `i comandi del lettore`() {
        assertEquals(VoiceCommand.Media(MediaAction.NEXT), VoiceParser.parse("prossima canzone"))
        assertEquals(VoiceCommand.Media(MediaAction.PREVIOUS), VoiceParser.parse("canzone precedente"))
        assertEquals(VoiceCommand.Media(MediaAction.PAUSE), VoiceParser.parse("metti in pausa"))
        assertEquals(VoiceCommand.Media(MediaAction.PLAY), VoiceParser.parse("riprendi"))
    }

    @Test
    fun `il volume, in due versi`() {
        assertEquals(VoiceCommand.Volume(up = true, steps = VoiceParser.VOLUME_STEPS), VoiceParser.parse("alza il volume"))
        assertEquals(VoiceCommand.Volume(up = false, steps = VoiceParser.VOLUME_STEPS), VoiceParser.parse("abbassa il volume"))
    }

    @Test
    fun `un volume contraddittorio non fa niente`() {
        // Capita sentendo una frase a meta'. Scegliere a caso fra alzare e
        // abbassare sarebbe peggio che non fare niente.
        assertNull(VoiceParser.parse("alza e abbassa il volume"))
    }

    @Test
    fun `fermare la musica non spegne AirScroll`() {
        // E' la confusione che farebbe disinstallare l'app: chiedere la pausa e
        // vedersi spegnere tutto il sistema di controllo.
        assertEquals(VoiceCommand.Media(MediaAction.PAUSE), VoiceParser.parse("ferma la musica"))
        assertEquals(VoiceCommand.Stop, VoiceParser.parse("spegni"))
        assertEquals(VoiceCommand.Stop, VoiceParser.parse("ferma tutto"))
    }

    @Test
    fun `una frase qualsiasi non e' un comando`() {
        // Il microfono sente anche la televisione e le conversazioni: la
        // risposta giusta, quasi sempre, e' non fare niente.
        assertNull(VoiceParser.parse("domani vado al mare"))
        assertNull(VoiceParser.parse(""))
        assertNull(VoiceParser.parse("   "))
        assertNull(VoiceParser.parse("che bella giornata"))
    }

    @Test
    fun `un verbo di riproduzione fuori contesto non fa partire niente`() {
        // Trovato da un test, non a tavolino: dopo aver accettato "metti" e
        // "manda" come verbi di riproduzione, "manda un messaggio a Marco"
        // faceva partire la musica. Ora quei verbi contano solo se nella frase
        // si parla anche di musica.
        assertNull(VoiceParser.parse("manda un messaggio a Marco"))
        assertNull(VoiceParser.parse("metti a posto la stanza"))

        assertEquals(VoiceCommand.Media(MediaAction.PLAY), VoiceParser.parse("metti la musica"))
        assertEquals(VoiceCommand.Media(MediaAction.PLAY), VoiceParser.parse("riprendi"))
    }

    @Test
    fun `nominare un'app senza chiedere di aprirla non basta`() {
        // "ieri su spotify c'era una canzone bellissima" non e' un ordine.
        assertNull(VoiceParser.parse("ieri su spotify ho sentito una cosa bella"))
    }

    @Test
    fun `aprire qualcosa che non conosciamo non fa niente`() {
        assertNull(VoiceParser.parse("apri il frigorifero"))
    }

    @Test
    fun `la normalizzazione fa il suo lavoro`() {
        assertEquals("perche si", VoiceParser.normalise("Perché sì!"))
        assertEquals("apri spotify", VoiceParser.normalise("  APRI   Spotify.  "))
    }

    @Test
    fun `le preferite su youtube restano su youtube`() {
        assertEquals(
            VoiceCommand.PlayFavourites(AppTarget.YOUTUBE),
            VoiceParser.parse("fai partire le mie canzoni preferite su youtube"),
        )
    }
}
