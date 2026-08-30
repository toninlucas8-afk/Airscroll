#!/usr/bin/env python3
"""Controlli sull'APK finito.

Due versioni pubblicate sono state inservibili per motivi che nessun test sul
codice poteva vedere: il modello prima non veniva impacchettato, poi veniva
impacchettato compresso. Entrambe le volte l'app si installava, la fotocamera
funzionava, e il riconoscimento era morto in silenzio.

Questo script guarda dentro l'APK finito, che e' l'unico posto dove quei guasti
esistono.
"""

import argparse
import struct
import sys
import zipfile

MODEL = "assets/gesture_recognizer.task"
MIN_MODEL_BYTES = 1_000_000
NATIVE_LIBRARY = "libmediapipe_tasks_vision_jni.so"

# Dal 2025 esistono telefoni Android con pagine di memoria da 16 KB. Una
# libreria nativa allineata a 4 KB semplicemente non si apre: `dlopen` fallisce
# e MediaPipe non parte, senza che nulla lo spieghi all'utente.
REQUIRED_ALIGNMENT = 16_384

PT_LOAD = 1


def fail(message: str) -> None:
    print(f"::error::{message}")
    sys.exit(1)


def load_alignments(blob: bytes) -> set[int]:
    """Allineamenti richiesti dai segmenti caricabili di un ELF a 64 bit."""
    if blob[:4] != b"\x7fELF":
        return set()
    if blob[4] != 2:  # solo ELF a 64 bit: le ABI a 32 bit non ci interessano
        return set()
    phoff = struct.unpack_from("<Q", blob, 0x20)[0]
    entsize = struct.unpack_from("<H", blob, 0x36)[0]
    count = struct.unpack_from("<H", blob, 0x38)[0]
    aligns = set()
    for index in range(count):
        offset = phoff + index * entsize
        if struct.unpack_from("<I", blob, offset)[0] == PT_LOAD:
            aligns.add(struct.unpack_from("<Q", blob, offset + 48)[0])
    return aligns


def check_model(apk: zipfile.ZipFile) -> None:
    if MODEL not in apk.namelist():
        present = [n for n in apk.namelist() if n.startswith("assets/")]
        print("Asset presenti:", present)
        fail(f"L'APK non contiene {MODEL}. Senza modello il riconoscimento non parte.")

    info = apk.getinfo(MODEL)
    if info.file_size < MIN_MODEL_BYTES:
        fail(f"Il modello e' troppo piccolo: {info.file_size} byte. Download troncato?")

    # MediaPipe apre il modello con `AssetManager.openFd()`, che funziona solo
    # su asset non compressi.
    if info.compress_type != zipfile.ZIP_STORED:
        fail(
            "Il modello e' compresso dentro l'APK. MediaPipe non riuscira' a "
            'caricarlo: serve `noCompress += "task"` nel modulo :app.'
        )
    print(f"modello    : presente, non compresso, {info.file_size:,} byte")


def check_native_libraries(apk: zipfile.ZipFile, require_16k: bool) -> None:
    entries = [n for n in apk.namelist() if n.endswith(f"/{NATIVE_LIBRARY}")]
    if not entries:
        fail(f"L'APK non contiene {NATIVE_LIBRARY}: il riconoscitore non ha il suo motore.")

    worst = None
    for name in sorted(entries):
        aligns = load_alignments(apk.read(name))
        if not aligns:
            print(f"libreria   : {name} (a 32 bit, allineamento non applicabile)")
            continue
        smallest = min(aligns)
        worst = smallest if worst is None else min(worst, smallest)
        pretty = ", ".join(hex(a) for a in sorted(aligns))
        print(f"libreria   : {name} allineamento {pretty}")

    if worst is None:
        return
    if worst < REQUIRED_ALIGNMENT:
        message = (
            f"{NATIVE_LIBRARY} e' allineata a {worst} byte: su un telefono con pagine "
            f"da 16 KB non si carica affatto, e il riconoscimento non parte. "
            f"Serve una versione di MediaPipe compilata per pagine da {REQUIRED_ALIGNMENT} byte."
        )
        if require_16k:
            fail(message)
        print(f"::warning::{message}")
    else:
        print(f"allineamento: {worst} byte, va bene anche sui telefoni a 16 KB")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("apk")
    parser.add_argument(
        "--allow-4k-pages",
        action="store_true",
        help="segnala l'allineamento a 4 KB come avviso invece che come errore",
    )
    args = parser.parse_args()

    with zipfile.ZipFile(args.apk) as apk:
        check_model(apk)
        check_native_libraries(apk, require_16k=not args.allow_4k_pages)
    print("APK verificato.")


if __name__ == "__main__":
    main()
