#!/usr/bin/env python3
"""Genera la tabella dei comandi vocali del manuale, leggendo il codice.

La tabella nel manuale e quella dentro l'app devono dire la stessa cosa, e
quella cosa deve essere vera. Nel codice c'e' gia' un test che verifica che ogni
frase del libretto faccia davvero quello che promette; qui si chiude il
cerchio, generando il pezzo di manuale da quello stesso libretto invece di
ricopiarlo a mano.

Un elenco ricopiato a mano invecchia in silenzio: si aggiunge un comando, ci si
dimentica del manuale, e da quel momento la documentazione mente su una cosa
che riguarda un microfono. Non e' il tipo di documento su cui ci si puo'
permettere di essere approssimativi.

Uso:
    python3 tools/genera_tabella_comandi.py          # controlla e basta
    python3 tools/genera_tabella_comandi.py --scrivi # aggiorna il manuale
"""

import re
import sys
from pathlib import Path

RADICE = Path(__file__).resolve().parent.parent
LIBRETTO = RADICE / "core/voice/src/main/java/dev/airscroll/core/voice/VoicePhrasebook.kt"
COMANDI = RADICE / "core/voice/src/main/java/dev/airscroll/core/voice/VoiceCommand.kt"
MANUALE = RADICE / "docs/manuale.html"

INIZIO = "<!-- TABELLA COMANDI: generata da tools/genera_tabella_comandi.py -->"
FINE = "<!-- FINE TABELLA COMANDI -->"

TITOLI = {
    "SPOTIFY": "Musica (Spotify)",
    "ALTRE_APP": "Aprire un'app",
    "LETTORE": "Quello che sta suonando, qualunque app sia",
    "VOLUME": "Volume",
    "AIRSCROLL": "AirScroll stesso",
}

EFFETTI = {
    "OpenApp": "Apre l'app.",
    "PlayFavourites": "Apre i brani salvati e manda il comando di riproduzione.",
    "PlayGenre": ("Cerca quel genere nell'app di musica e manda il comando di "
                  "riproduzione. Quello che parte dipende da cosa l'app mette in cima "
                  "ai risultati, non da una scelta di AirScroll."),
    "Media(MediaAction.NEXT)": "Passa al brano successivo.",
    "Media(MediaAction.PREVIOUS)": "Torna al brano precedente.",
    "Media(MediaAction.PAUSE)": "Mette in pausa.",
    "Media(MediaAction.PLAY)": "Riprende la riproduzione.",
    "Volume(up = true": "Alza il volume di tre gradini.",
    "Volume(up = false": "Abbassa il volume di tre gradini.",
    "Stop": "Spegne AirScroll.",
}


def generi() -> list[str]:
    """I nomi parlati dei generi, nell'ordine dichiarato nell'enum."""
    testo = COMANDI.read_text(encoding="utf-8")
    blocco = testo[testo.index("enum class Genre("):]
    blocco = blocco[:blocco.index("\n}\n")]
    return [m.group(1) for m in re.finditer(r'^\s{4}[A-Z_]+\(listOf\("([^"]+)"', blocco, re.M)]


def app_parlate() -> list[str]:
    testo = COMANDI.read_text(encoding="utf-8")
    blocco = testo[testo.index("enum class AppTarget("):]
    blocco = blocco[:blocco.index("\n}\n")]
    return [m.group(1) for m in re.finditer(r'spokenNames = listOf\("([^"]+)"', blocco)]


def voci() -> list[tuple[str, str, str, str]]:
    """(gruppo, frase, alternative, effetto) leggendo il libretto Kotlin."""
    testo = LIBRETTO.read_text(encoding="utf-8")
    risultato: list[tuple[str, str, str, str]] = []

    for blocco in re.finditer(
        r"Phrase\(\s*group = Group\.(\w+),\s*canonical = ([^\n]+?),\s*command = ([^\n]+?),"
        r"(?:\s*alternatives = listOf\((.*?)\),)?\s*\)",
        testo,
        re.S,
    ):
        gruppo, frase, comando, alternative = blocco.groups()
        risultato.append((gruppo, frase.strip(), (alternative or "").strip(), comando.strip()))
    return risultato


def effetto_di(comando: str) -> str:
    for chiave, testo in EFFETTI.items():
        if chiave in comando:
            return testo
    sys.exit(f"comando senza descrizione: {comando}")


def pulisci(frase: str) -> str:
    """Da un letterale Kotlin alla frase, risolvendo l'interpolazione."""
    frase = frase.strip().strip('"')
    frase = frase.replace("${genere.spokenNames.first()}", "GENERE")
    frase = frase.replace("${app.spokenNames.first()}", "APP")
    return frase


def costruisci() -> str:
    righe = [INIZIO, "<table>", "  <tr><th style=\"width:62mm\">Cosa dire</th><th>Cosa succede</th></tr>"]
    gruppo_corrente = None

    for gruppo, frase, alternative, comando in voci():
        if gruppo != gruppo_corrente:
            gruppo_corrente = gruppo
            righe.append(
                f'  <tr><td colspan="2" style="background:#eef4f0;"><strong>{TITOLI[gruppo]}</strong></td></tr>'
            )

        testo = pulisci(frase)
        if "GENERE" in testo:
            elenco = ", ".join(generi())
            testo = testo.replace("GENERE", "<em>genere</em>")
            effetto = (
                f"{effetto_di(comando)} I generi riconosciuti sono nove: {elenco}."
            )
        elif "APP" in testo:
            elenco = ", ".join(a for a in app_parlate() if a != "spotify")
            testo = testo.replace("APP", "<em>app</em>")
            effetto = f"{effetto_di(comando)} Le app riconosciute sono: {elenco}."
        else:
            effetto = effetto_di(comando)

        varianti = [pulisci(v) for v in re.findall(r'"([^"]+)"', alternative)]
        varianti = [v for v in varianti if "GENERE" not in v and "APP" not in v]
        if varianti:
            effetto += " Vanno bene anche: " + ", ".join(f"«{v}»" for v in varianti) + "."

        righe.append(f"  <tr><td>«{testo}»</td><td>{effetto}</td></tr>")

    righe.append("</table>")
    righe.append(FINE)

    # Due controlli di sanita': questo file legge il Kotlin con delle
    # espressioni regolari, e se un giorno il libretto cambiasse forma
    # potrebbe smettere di trovare le voci senza dirlo. Meglio fermarsi qui
    # che generare in silenzio una tabella dimezzata.
    trovate = len(voci())
    if trovate < 10:
        sys.exit(f"lette solo {trovate} voci dal libretto: la forma e' cambiata?")
    mancanti = [g for g in TITOLI if g not in {v[0] for v in voci()}]
    if mancanti:
        sys.exit(f"gruppi non trovati nel libretto: {mancanti}")

    return "\n".join(righe)


def main() -> int:
    manuale = MANUALE.read_text(encoding="utf-8")
    if INIZIO not in manuale or FINE not in manuale:
        print("Nel manuale mancano i segnaposto della tabella.", file=sys.stderr)
        return 1

    nuova = costruisci()
    inizio = manuale.index(INIZIO)
    fine = manuale.index(FINE) + len(FINE)
    attuale = manuale[inizio:fine]

    if attuale == nuova:
        print("La tabella del manuale e' allineata al codice.")
        return 0

    if "--scrivi" not in sys.argv:
        print(
            "La tabella del manuale non corrisponde al codice.\n"
            "Rigenerala con: python3 tools/genera_tabella_comandi.py --scrivi",
            file=sys.stderr,
        )
        return 1

    MANUALE.write_text(manuale[:inizio] + nuova + manuale[fine:], encoding="utf-8")
    print("Tabella del manuale rigenerata.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
