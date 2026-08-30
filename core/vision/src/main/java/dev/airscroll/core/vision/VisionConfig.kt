package dev.airscroll.core.vision

/**
 * Parametri del riconoscitore.
 *
 * In attesa (stato giallo) alziamo le soglie: cerchiamo solo un pollice in su
 * ben chiaro, e non vogliamo falsi positivi. In stato attivo le abbassiamo per
 * non perdere il tracking durante il movimento.
 */
data class VisionConfig(
    val modelAssetPath: String = DEFAULT_MODEL_ASSET,
    val preferGpu: Boolean = true,
    val numHands: Int = 1,
    val minHandDetectionConfidence: Float = 0.5f,
    val minHandPresenceConfidence: Float = 0.5f,
    val minTrackingConfidence: Float = 0.5f,
    /**
     * Soglia minima per riportare un gesto.
     *
     * Volutamente bassa: chi decide davvero se il gesto vale e' il motore, che
     * applica un'isteresi (severo per partire, indulgente per proseguire).
     * Filtrare troppo qui significherebbe buttare via l'informazione prima che
     * qualcuno possa usarla.
     */
    val minGestureConfidence: Float = 0.32f,
    /**
     * Specchia l'asse orizzontale. Con la fotocamera frontale serve sempre:
     * l'utente ragiona come davanti a uno specchio. Costa zero perche' e' una
     * sottrazione sulle coordinate, non una trasformazione del bitmap.
     */
    val mirrorHorizontally: Boolean = true,
    /**
     * Allega i 21 punti grezzi a ogni fotogramma. Serve solo al laboratorio:
     * a regime resta spento perche' costa allocazioni inutili.
     */
    val includeLandmarks: Boolean = false,
) {
    companion object {
        const val DEFAULT_MODEL_ASSET = "gesture_recognizer.task"
        val Default = VisionConfig()
    }
}
