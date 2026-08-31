#!/usr/bin/env python3
"""Ogni modulo che ha dei test deve anche dichiarare la libreria per eseguirli.

Sembra impossibile sbagliarsi, ed e' successo due volte nello stesso giorno:
`core/camera` e `core/settings` hanno ricevuto una cartella `src/test` senza che
nessuno aggiungesse `testImplementation(libs.junit)` al loro `build.gradle.kts`.
Il compilatore Kotlin non dice "manca una dipendenza": dice `Unresolved
reference 'junit'` venti volte di fila, e la build muore dopo cinquanta secondi
per una riga mancante.

Questo controllo costa un decimo di secondo e guarda la stessa cosa: se un
modulo ha almeno un file di test, il suo file di build deve nominare junit.
Guarda anche il caso opposto, meno grave ma sintomo della stessa distrazione:
una dipendenza di test dichiarata in un modulo che di test non ne ha.
"""

import sys
from pathlib import Path

DIPENDENZA = "testImplementation(libs.junit)"


def moduli(radice: Path) -> list[Path]:
    return sorted(p.parent for p in radice.glob("*/*/build.gradle.kts"))


def problemi_del_modulo(modulo: Path, radice: Path) -> list[str]:
    nome = modulo.relative_to(radice)
    testi = list((modulo / "src" / "test").rglob("*.kt"))
    build = (modulo / "build.gradle.kts").read_text(encoding="utf-8")
    dichiarata = DIPENDENZA in build

    if testi and not dichiarata:
        quanti = len(testi)
        return [
            f"{nome}: ha {quanti} file di test ma non dichiara {DIPENDENZA}"
        ]
    if dichiarata and not testi:
        return [f"{nome}: dichiara {DIPENDENZA} ma non ha nessun file di test"]
    return []


def main() -> int:
    radice = Path(__file__).resolve().parent.parent
    trovati: list[str] = []
    for modulo in moduli(radice):
        trovati.extend(problemi_del_modulo(modulo, radice))

    if trovati:
        print("Moduli con i test cablati male:", file=sys.stderr)
        for problema in trovati:
            print(f"  - {problema}", file=sys.stderr)
        return 1

    print("Test cablati: ogni modulo con dei test dichiara junit.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
