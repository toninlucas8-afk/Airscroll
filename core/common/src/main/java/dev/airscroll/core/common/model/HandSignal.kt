package dev.airscroll.core.common.model

/**
 * Gesti canonici riconosciuti dal modello MediaPipe Gesture Recognizer.
 *
 * I nomi delle etichette prodotte dal modello sono mappati qui una volta sola,
 * cosi' il resto del codice non dipende dalle stringhe della libreria.
 */
enum class HandSignal {
    NONE,
    THUMB_UP,
    THUMB_DOWN,
    CLOSED_FIST,
    OPEN_PALM,
    POINTING_UP,
    VICTORY,
    LOVE;

    companion object {
        fun fromMediaPipeLabel(label: String?): HandSignal = when (label) {
            "Thumb_Up" -> THUMB_UP
            "Thumb_Down" -> THUMB_DOWN
            "Closed_Fist" -> CLOSED_FIST
            "Open_Palm" -> OPEN_PALM
            "Pointing_Up" -> POINTING_UP
            "Victory" -> VICTORY
            "ILoveYou" -> LOVE
            else -> NONE
        }
    }
}
