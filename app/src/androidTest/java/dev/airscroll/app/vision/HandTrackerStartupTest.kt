package dev.airscroll.app.vision

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.airscroll.core.vision.MediaPipeHandTracker
import dev.airscroll.core.vision.VisionConfig
import dev.airscroll.core.vision.VisionDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Il test che sarebbe servito quattro versioni fa.
 *
 * Quattro APK pubblicati e inservibili, per quattro cause diverse: il modello
 * non veniva impacchettato, poi veniva impacchettato compresso, poi la libreria
 * nativa non era allineata a 16 KB, poi R8 rompeva l'inizializzazione di
 * MediaPipe. Nessuno di questi guasti e' visibile ai test unitari, alla lint o
 * alla compilazione: esistono solo dentro l'APK finito, in esecuzione.
 *
 * Questo test gira sulla variante *release* - la stessa che viene pubblicata -
 * e fa la sola cosa che li avrebbe presi tutti: avvia il riconoscitore vero e
 * gli passa dei fotogrammi.
 */
@RunWith(AndroidJUnit4::class)
class HandTrackerStartupTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Il modello dev'essere apribile con un descrittore di file.
     *
     * E' esattamente cio' che fa `setModelAssetPath`, e fallisce se l'asset e'
     * assente oppure compresso dentro l'APK. Entrambi i casi sono gia'
     * successi.
     */
    @Test
    fun ilModelloSiApreComeDescrittoreDiFile() {
        val diagnostics = VisionDiagnostics.collect(context)
        assertEquals(
            "Il modello non e' utilizzabile.\n${diagnostics.report()}",
            VisionDiagnostics.ModelState.OK,
            diagnostics.modelState,
        )
        assertTrue(
            "Il modello e' troppo piccolo: ${diagnostics.modelBytes} byte.",
            diagnostics.modelBytes > 1_000_000L,
        )
    }

    /** La libreria nativa dev'essere caricabile su questo dispositivo. */
    @Test
    fun laLibreriaNativaSiCarica() {
        val diagnostics = VisionDiagnostics.collect(context)
        assertTrue(
            "La libreria nativa non si carica.\n${diagnostics.report()}",
            diagnostics.nativeLibraryLoaded,
        )
    }

    /**
     * Il riconoscitore si crea davvero.
     *
     * E' il passo che la 0.4.2 non superava: R8 rinominava flogger, flogger non
     * trovava piu' il chiamante sullo stack, l'inizializzatore statico di
     * `com.google.mediapipe.framework.Graph` esplodeva e non partiva niente.
     * Un guasto che si vede solo nella build di release, in esecuzione.
     */
    @Test
    fun ilRiconoscitoreSiAvvia() {
        val tracker = tracker()
        try {
            tracker.start()
            assertTrue(
                "Il riconoscitore non si e' avviato.\n" +
                    (tracker.failure?.report() ?: "nessuna diagnosi disponibile"),
                tracker.isReady,
            )
        } finally {
            tracker.stop()
        }
    }

    /**
     * L'inferenza risponde.
     *
     * Crearsi non basta: su alcuni dispositivi il delegate GPU si crea e poi non
     * restituisce mai un risultato. L'unica prova che la catena funzioni e' che
     * un fotogramma entri e una risposta esca.
     */
    @Test
    fun unFotogrammaEntraEUnaRispostaEsce() {
        val tracker = tracker()
        try {
            tracker.start()
            assertTrue(
                "Il riconoscitore non si e' avviato.\n" +
                    (tracker.failure?.report() ?: "nessuna diagnosi disponibile"),
                tracker.isReady,
            )

            val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
            val deadline = System.currentTimeMillis() + TIMEOUT_MS
            var timestamp = 1L
            while (tracker.stats().results == 0L && System.currentTimeMillis() < deadline) {
                tracker.submit(bitmap, timestamp)
                timestamp += FRAME_INTERVAL_MS
                Thread.sleep(FRAME_INTERVAL_MS)
            }

            val stats = tracker.stats()
            assertTrue(
                "Nessuna risposta dal modello dopo $TIMEOUT_MS ms. " +
                    "Inviati ${stats.submitted}, analizzati ${stats.results}, " +
                    "scartati ${stats.droppedBusy}, bloccati ${stats.stalls}. " +
                    "Ultimo errore: ${stats.lastError ?: "nessuno"}",
                stats.results > 0L,
            )
        } finally {
            tracker.stop()
        }
    }

    private fun tracker() = MediaPipeHandTracker(
        context = context,
        // Sull'emulatore la GPU non e' affidabile e non e' cio' che si vuole
        // provare qui: la domanda e' se la catena parte, non su quale delegate.
        config = VisionConfig.Default.copy(preferGpu = false),
    )

    private companion object {
        const val WIDTH = 320
        const val HEIGHT = 240
        const val FRAME_INTERVAL_MS = 60L
        const val TIMEOUT_MS = 20_000L
    }
}
