package dev.airscroll.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La tabella dei comandi non puo' mentire.
 *
 * E' il test che rende l'elenco una promessa invece di una dichiarazione: ogni
 * frase scritta nel libretto viene data in pasto al riconoscitore vero, e deve
 * produrre esattamente il comando che il libretto dichiara. Se qualcuno cambia
 * il vocabolario senza aggiornare la tabella - o aggiunge alla tabella una
 * frase che il riconoscitore non capisce - la build si ferma qui.
 *
 * Senza questo, la tabella nel manuale e quella dentro l'app sarebbero due
 * documenti che invecchiano per conto loro, e chi legge non avrebbe modo di
 * sapere quale delle due e' ancora vera.
 */
class VoicePhrasebookTest {

    @Test
    fun `ogni frase del libretto fa quello che il libretto dice`() {
        val sbagliate = VoicePhrasebook.everySpokenForm().mapNotNull { (frase, atteso) ->
            val ottenuto = VoiceParser.parse(frase)
            if (ottenuto == atteso) null else "\"$frase\": atteso $atteso, ottenuto $ottenuto"
        }
        assertEquals("frasi che non fanno quello che promettono", emptyList<String>(), sbagliate)
    }

    @Test
    fun `il libretto copre tutti i generi`() {
        // Se qualcuno aggiunge un genere al vocabolario, deve comparire anche
        // nella tabella: un comando che funziona ma non e' documentato e' un
        // comando che nessuno usera' mai.
        val generiInTabella = VoicePhrasebook.entries
            .mapNotNull { (it.command as? VoiceCommand.PlayGenre)?.genre }
            .toSet()
        assertEquals(Genre.entries.toSet(), generiInTabella)
    }

    @Test
    fun `il libretto copre tutte le app`() {
        val appInTabella = VoicePhrasebook.entries
            .mapNotNull { (it.command as? VoiceCommand.OpenApp)?.target }
            .toSet()
        assertEquals(AppTarget.entries.toSet(), appInTabella)
    }

    @Test
    fun `ogni gruppo ha almeno un comando`() {
        // Un gruppo vuoto sarebbe un titolo di sezione senza niente sotto.
        VoicePhrasebook.Group.entries.forEach { gruppo ->
            assertTrue("gruppo vuoto: $gruppo", VoicePhrasebook.of(gruppo).isNotEmpty())
        }
    }

    @Test
    fun `nessuna frase e' duplicata`() {
        // Due frasi identiche che promettono comandi diversi sarebbero una
        // contraddizione, e vincerebbe quella che capita per prima.
        val tutte = VoicePhrasebook.everySpokenForm().map { it.first }
        val doppie = tutte.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertEquals(emptySet<String>(), doppie)
    }

    @Test
    fun `fuori dall'elenco non succede niente`() {
        // La controprova del vocabolario chiuso: frasi che assomigliano a
        // comandi ma non lo sono.
        listOf(
            "apri il frigorifero",
            "manda un messaggio a Marco",
            "che musica c'era ieri",
            "domani vado al mare",
            "chiama mia madre",
            "cerca una ricetta",
        ).forEach { frase ->
            assertNull("questa non doveva fare niente: \"$frase\"", VoiceParser.parse(frase))
        }
    }
}
