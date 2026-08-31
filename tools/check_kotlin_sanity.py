#!/usr/bin/env python3
"""Controlli strutturali sui sorgenti Kotlin, prima di compilare.

Non sostituisce il compilatore: prende una famiglia di errori sola, quella che
nasce dalle modifiche fatte con sostituzioni automatiche di testo. Quando
l'aggancio di una sostituzione cade a meta' di una dichiarazione, il risultato
non e' un errore evidente da rileggere: e' un'annotazione duplicata su una
funzione e una sparita da quella sotto.

E' gia' successo: `@androidx.annotation.StringRes` finita due volte sulla stessa
funzione ha fatto fallire la build in CI dopo cinque minuti, per una cosa
visibile in un decimo di secondo.
"""

import re
import sys
from pathlib import Path

ANNOTATION = re.compile(r"^\s*@[\w.]+")


def controlla(percorso: Path) -> list[str]:
    problemi = []
    righe = percorso.read_text(encoding="utf-8").splitlines()

    precedente = None
    for numero, riga in enumerate(righe, start=1):
        if ANNOTATION.match(riga):
            corrente = riga.strip()
            if corrente == precedente:
                problemi.append(
                    f"{percorso}:{numero} annotazione ripetuta due volte: {corrente}"
                )
            precedente = corrente
        elif riga.strip():
            precedente = None

    graffe = sum(r.count("{") - r.count("}") for r in righe)
    if graffe != 0:
        verso = "aperte" if graffe > 0 else "chiuse"
        problemi.append(f"{percorso}: {abs(graffe)} graffe {verso} in piu'")

    return problemi


def main() -> None:
    radici = [Path(p) for p in (sys.argv[1:] or ["app", "core", "apps"])]
    file_kt = sorted(f for radice in radici for f in radice.rglob("*.kt"))
    if not file_kt:
        print("::error::Nessun sorgente Kotlin trovato: il controllo non sta guardando niente.")
        sys.exit(1)

    problemi = [p for f in file_kt for p in controlla(f)]
    for problema in problemi:
        print(f"::error::{problema}")

    print(f"{len(file_kt)} file Kotlin controllati, {len(problemi)} problemi.")
    sys.exit(1 if problemi else 0)


if __name__ == "__main__":
    main()
