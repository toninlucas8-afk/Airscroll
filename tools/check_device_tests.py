#!/usr/bin/env python3
"""Pretende che i test su dispositivo siano davvero stati eseguiti.

Un lavoro di CI verde non vuol dire che qualcosa sia stato verificato: se
nessun test viene trovato, `connectedAndroidTest` passa in silenzio ed e'
indistinguibile da una verifica riuscita. E' lo stesso vizio che ha lasciato
passare quattro APK inservibili - un controllo che non controlla niente e non
lo dice.

Qui si legge il risultato vero e si pretende un numero minimo di test.
"""

import glob
import sys
import xml.etree.ElementTree as ET

ATTESI = 4
CARTELLE = (
    "app/build/outputs/androidTest-results/connected/**/*.xml",
    "app/build/outputs/androidTest-results/**/*.xml",
)


def main() -> None:
    file_xml = sorted({p for schema in CARTELLE for p in glob.glob(schema, recursive=True)})
    if not file_xml:
        print("::error::Nessun risultato dei test su dispositivo. I test non sono stati eseguiti.")
        sys.exit(1)

    totale = falliti = errori = saltati = 0
    for percorso in file_xml:
        radice = ET.parse(percorso).getroot()
        suite = [radice] if radice.tag == "testsuite" else radice.findall(".//testsuite")
        for s in suite:
            totale += int(s.get("tests", 0))
            falliti += int(s.get("failures", 0))
            errori += int(s.get("errors", 0))
            saltati += int(s.get("skipped", 0))
            for caso in s.findall("testcase"):
                esito = "OK"
                if caso.find("failure") is not None:
                    esito = "FALLITO"
                elif caso.find("error") is not None:
                    esito = "ERRORE"
                elif caso.find("skipped") is not None:
                    esito = "saltato"
                print(f"  {esito:8s} {caso.get('classname', '?').split('.')[-1]}.{caso.get('name')}")

    eseguiti = totale - saltati
    print(f"\ntest su dispositivo: {totale} totali, {saltati} saltati, "
          f"{falliti} falliti, {errori} in errore")

    if falliti or errori:
        print("::error::Il riconoscimento non funziona sul dispositivo.")
        sys.exit(1)
    if eseguiti < ATTESI:
        print(f"::error::Eseguiti solo {eseguiti} test su {ATTESI} attesi. "
              "Un lavoro verde senza test eseguiti non verifica niente.")
        sys.exit(1)
    print(f"Il riconoscimento si avvia e risponde: {eseguiti} test eseguiti sul dispositivo.")


if __name__ == "__main__":
    main()
