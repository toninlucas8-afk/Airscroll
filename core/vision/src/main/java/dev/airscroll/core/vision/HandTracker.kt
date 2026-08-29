package dev.airscroll.core.vision

import android.graphics.Bitmap
import dev.airscroll.core.common.model.HandFrame
import kotlinx.coroutines.flow.SharedFlow

/**
 * Astrazione sul riconoscitore di mani.
 *
 * Il motore dipende solo da questa interfaccia: se un domani si vuole
 * sostituire MediaPipe con un altro modello, basta una nuova implementazione.
 */
interface HandTracker {

    /** Fotogrammi analizzati, gia' normalizzati e specchiati. */
    val frames: SharedFlow<HandFrame>

    /** Ultimo errore non fatale, per mostrarlo in UI. */
    val lastError: String?

    /** true se il delegate GPU e' realmente in uso. */
    val usingGpu: Boolean

    fun start()

    /**
     * Invia un fotogramma all'analisi. Il bitmap deve essere gia' ruotato e
     * specchiato. Le chiamate sono non bloccanti: se il modello e' occupato il
     * fotogramma viene scartato.
     */
    fun submit(bitmap: Bitmap, timestampMs: Long)

    fun stop()
}
