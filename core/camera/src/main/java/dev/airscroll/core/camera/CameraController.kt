package dev.airscroll.core.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import android.hardware.camera2.CaptureRequest
import android.util.Range
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import dev.airscroll.core.common.model.PerformanceMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Gestisce la fotocamera frontale in sola analisi.
 *
 * Punti chiave per il consumo:
 * - nessuna Preview quando gira in background (la superficie grafica costa);
 * - risoluzione di analisi piccola, scelta dal [PerformanceMode];
 * - cadenza limitata via software: in attesa bastano 6-8 fotogrammi al secondo;
 * - `unbind()` chiude davvero la fotocamera, non la mette solo in pausa.
 */
class CameraController(context: Context) {

    private val appContext = context.applicationContext
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var executor: ExecutorService? = null

    private val minIntervalMs = AtomicInteger(66)
    private var lastFrameAt = 0L
    private var reusableMatrix = Matrix()

    private val stillness = StillnessDetector()
    private var camera: Camera? = null
    private val targetFps = AtomicInteger(15)
    private val sensorFrameRate = AtomicBoolean(true)

    /**
     * Se true, i fotogrammi identici al precedente non vengono nemmeno
     * convertiti.
     *
     * Lo decide chi usa la fotocamera, non questa classe, e per una ragione
     * precisa: **un pollice in su tenuto fermo e' a tutti gli effetti una scena
     * ferma.** Saltarlo significherebbe non riconoscere proprio il gesto che si
     * sta aspettando. Va acceso solo quando davvero non c'e' niente in corso.
     */
    private val skipStillFrames = AtomicBoolean(false)

    /** Quanti fotogrammi sono stati risparmiati perche' la scena era ferma. */
    val framesSkippedStill: Long get() = stillness.totalSkipped

    var isBound: Boolean = false
        private set

    /**
     * Accende la fotocamera e comincia ad analizzare.
     *
     * @param preview opzionale: usato solo dalla schermata di calibrazione.
     * @param onFrame chiamato su un thread di lavoro con un bitmap gia' ruotato
     *   in verticale. Il chiamante non deve conservarne il riferimento.
     */
    fun bind(
        lifecycleOwner: LifecycleOwner,
        mode: PerformanceMode,
        targetFps: Int,
        preview: Preview? = null,
        onError: (Throwable) -> Unit = {},
        onFrame: (Bitmap, Long) -> Unit,
    ) {
        setTargetFps(targetFps)
        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener({
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                val workExecutor = executor ?: Executors.newSingleThreadExecutor().also { executor = it }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(mode.analysisWidth, mode.analysisHeight))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()

                imageAnalysis.setAnalyzer(workExecutor) { proxy ->
                    dispatch(proxy, onFrame)
                }

                cameraProvider.unbindAll()
                val useCases = buildList<UseCase> {
                    add(imageAnalysis)
                    preview?.let { add(it) }
                }.toTypedArray()

                analysis = imageAnalysis
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    *useCases,
                )
                isBound = true
                applySensorFrameRate()
            } catch (t: Throwable) {
                Log.e(TAG, "Impossibile aprire la fotocamera", t)
                isBound = false
                onError(t)
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    /**
     * Accende o spegne il salto dei fotogrammi immobili.
     *
     * Da tenere spento appena si vede una mano: durante un gesto tenuto la
     * scena e' ferma per definizione.
     */
    fun setSkipStillFrames(enabled: Boolean) {
        if (skipStillFrames.getAndSet(enabled) != enabled && !enabled) stillness.reset()
    }

    /** Cambia la cadenza senza ricreare la sessione: e' il caso giallo -> verde. */
    fun setTargetFps(fps: Int) {
        val safeFps = fps.coerceIn(2, 60)
        minIntervalMs.set(1000 / safeFps)
        targetFps.set(safeFps)
        applySensorFrameRate()
    }

    /**
     * Chiede al **sensore** di andare piano, invece di scartare i fotogrammi in
     * eccesso.
     *
     * Fino alla 0.5.1 rallentare voleva dire soltanto ignorare i fotogrammi di
     * troppo: il sensore e l'elaborazione dell'immagine continuavano a lavorare
     * a pieno regime, e ne pagavamo il conto senza usarne il risultato.
     *
     * `Camera2CameraControl` permette di cambiare l'intervallo di acquisizione
     * sulla sessione **gia' aperta**. E' la differenza fra questa correzione e
     * quella che temevo: nessun riavvio della fotocamera, quindi nessuno scatto
     * al momento dell'attivazione, che e' proprio l'istante in cui la prontezza
     * conta di piu'.
     *
     * Se il telefono non accetta l'intervallo richiesto, non succede niente di
     * male: resta il filtro software di prima.
     */
    @OptIn(ExperimentalCamera2Interop::class)
    private fun applySensorFrameRate() {
        if (!sensorFrameRate.get()) return
        val control = camera?.cameraControl ?: return
        val fps = targetFps.get()
        runCatching {
            Camera2CameraControl.from(control).setCaptureRequestOptions(
                CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                        Range(fps, fps),
                    )
                    .build()
            )
        }.onFailure { Log.i(TAG, "Cadenza del sensore non applicabile: resta il filtro software", it) }
    }

    /** Abilita o disabilita il controllo della cadenza del sensore. */
    fun setSensorFrameRate(enabled: Boolean) {
        if (sensorFrameRate.getAndSet(enabled) == enabled) return
        if (enabled) applySensorFrameRate() else clearSensorFrameRate()
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun clearSensorFrameRate() {
        val control = camera?.cameraControl ?: return
        runCatching {
            Camera2CameraControl.from(control)
                .setCaptureRequestOptions(CaptureRequestOptions.Builder().build())
        }
    }

    fun unbind() {
        stillness.reset()
        runCatching {
            analysis?.clearAnalyzer()
            provider?.unbindAll()
        }
        analysis = null
        camera = null
        isBound = false
        lastFrameAt = 0L
    }

    /** Da chiamare quando il servizio muore: chiude anche il thread di lavoro. */
    fun release() {
        unbind()
        executor?.shutdown()
        executor = null
        provider = null
    }

    private fun dispatch(proxy: ImageProxy, onFrame: (Bitmap, Long) -> Unit) {
        try {
            val now = SystemClock.uptimeMillis()
            if (now - lastFrameAt < minIntervalMs.get()) return
            lastFrameAt = now

            // Il confronto va fatto **prima** di `toBitmap()`: anche solo
            // convertire un fotogramma da YUV a RGB costa piu' di leggere
            // qualche centinaio di byte di luminosita'.
            if (skipStillFrames.get() && !stillness.shouldAnalyse(sampleScene(proxy))) return

            val source = proxy.toBitmap()
            val rotation = proxy.imageInfo.rotationDegrees
            val upright = if (rotation == 0) {
                source
            } else {
                reusableMatrix.reset()
                reusableMatrix.postRotate(rotation.toFloat())
                Bitmap.createBitmap(source, 0, 0, source.width, source.height, reusableMatrix, false)
            }
            onFrame(upright, now)
        } catch (t: Throwable) {
            Log.w(TAG, "Fotogramma scartato", t)
        } finally {
            proxy.close()
        }
    }

    /**
     * Campiona la scena su una griglia grossolana, per capire se e' cambiata.
     *
     * L'analisi e' configurata in RGBA, quindi c'e' un piano solo e si legge il
     * **canale rosso** di 256 punti: non e' la luminanza vera, ma per la
     * domanda a cui deve rispondere - "si e' mosso qualcosa?" - va benissimo, e
     * costa un byte per punto invece di quattro.
     *
     * Duecentocinquantasei letture contro i milioni di byte di un fotogramma
     * intero, e soprattutto contro la conversione che verrebbe dopo.
     */
    private fun sampleScene(proxy: ImageProxy): IntArray {
        val plane = proxy.planes.firstOrNull() ?: return IntArray(0)
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = proxy.width
        val height = proxy.height
        if (width <= 0 || height <= 0 || rowStride <= 0) return IntArray(0)

        val samples = IntArray(GRID * GRID)
        var index = 0
        for (row in 0 until GRID) {
            val y = (height - 1) * row / (GRID - 1)
            for (column in 0 until GRID) {
                val x = (width - 1) * column / (GRID - 1)
                val position = y * rowStride + x * pixelStride
                samples[index++] = if (position < buffer.limit()) {
                    buffer.get(position).toInt() and 0xFF
                } else {
                    0
                }
            }
        }
        return samples
    }

    private companion object {
        const val TAG = "AirScroll/Camera"

        /** Lato della griglia di campionamento: 16x16 = 256 punti. */
        const val GRID = 16
    }
}
