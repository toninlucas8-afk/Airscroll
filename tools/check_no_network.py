#!/usr/bin/env python3
"""Pretende che l'app non possa raggiungere la rete.

"Tutto gira offline" e' la promessa centrale di AirScroll, e fino alla 0.5.0
era solo una promessa: il codice non apriva nessuna connessione, ma l'APK
dichiarava comunque il permesso INTERNET, ereditato dal manifest di una
libreria attraverso la fusione dei manifest. Un permesso e' una possibilita', e
nessuno che guardi l'app dall'esterno puo' distinguere "non lo usa" da "non
l'ho ancora visto usarlo".

Senza il permesso, invece, Android **impedisce** di aprire un socket. La
promessa diventa una proprieta' del pacchetto, verificabile da chiunque in dieci
secondi.

Questo controllo legge il manifest fuso - quello che finisce davvero nell'APK,
non quello che scriviamo noi - e fallisce se un aggiornamento di libreria
dovesse reintrodurre un permesso di rete.
"""

import glob
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"

VIETATI = {
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE",
    "android.permission.CHANGE_NETWORK_STATE",
    "android.permission.CHANGE_WIFI_STATE",
}


def main() -> None:
    schemi = [
        "app/build/intermediates/merged_manifest*/**/AndroidManifest.xml",
        "app/build/intermediates/**/merged_manifest*/**/AndroidManifest.xml",
    ]
    trovati = sorted({p for schema in schemi for p in glob.glob(schema, recursive=True)})
    if not trovati:
        print("::error::Manifest fuso non trovato: il controllo non sta guardando niente.")
        sys.exit(1)

    problemi = []
    for percorso in trovati:
        radice = ET.parse(percorso).getroot()
        dichiarati = {
            elemento.get(f"{ANDROID}name")
            for elemento in radice.findall("uses-permission")
        }
        for permesso in sorted(VIETATI & dichiarati):
            problemi.append(f"{percorso}: dichiara {permesso}")
        print(f"{percorso}: {len(dichiarati)} permessi, nessuno di rete"
              if not (VIETATI & dichiarati) else f"{percorso}: PERMESSI DI RETE PRESENTI")

    if problemi:
        for problema in problemi:
            print(f"::error::{problema}")
        print("::error::AirScroll deve restare senza accesso alla rete. Se una libreria "
              "nuova lo ha reintrodotto, va tolto con tools:node=\"remove\" nel manifest.")
        sys.exit(1)

    print(f"{len(trovati)} manifest controllati: l'app non puo' raggiungere la rete.")


if __name__ == "__main__":
    main()
