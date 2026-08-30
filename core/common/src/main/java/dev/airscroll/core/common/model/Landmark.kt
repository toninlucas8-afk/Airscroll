package dev.airscroll.core.common.model

/**
 * Un punto della mano in coordinate normalizzate.
 *
 * MediaPipe ne restituisce 21: polso, e per ogni dito le tre articolazioni piu'
 * la punta. Il motore ne usa solo quattro per seguire il palmo, ma il
 * laboratorio li registra tutti: sono loro il materiale con cui si capisce
 * *perche'* un gesto non viene riconosciuto.
 */
data class Landmark(val x: Float, val y: Float, val z: Float)

/** Indici dei punti, come li numera MediaPipe. */
object HandLandmarks {
    const val WRIST = 0
    const val THUMB_CMC = 1
    const val THUMB_MCP = 2
    const val THUMB_IP = 3
    const val THUMB_TIP = 4
    const val INDEX_MCP = 5
    const val INDEX_PIP = 6
    const val INDEX_DIP = 7
    const val INDEX_TIP = 8
    const val MIDDLE_MCP = 9
    const val MIDDLE_PIP = 10
    const val MIDDLE_DIP = 11
    const val MIDDLE_TIP = 12
    const val RING_MCP = 13
    const val RING_PIP = 14
    const val RING_DIP = 15
    const val RING_TIP = 16
    const val PINKY_MCP = 17
    const val PINKY_PIP = 18
    const val PINKY_DIP = 19
    const val PINKY_TIP = 20
    const val COUNT = 21
}
