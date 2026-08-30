package dev.airscroll.core.vision

import android.content.Context
import android.os.Build
import android.system.Os
import android.system.OsConstants

/**
 * Perche' il riconoscitore non e' partito.
 *
 * Fino alla 0.4.1 l'app rispondeva sempre la stessa cosa - "manca il modello
 * MediaPipe" - qualunque fosse la causa vera. Era una diagnosi inventata: il
 * messaggio dell'eccezione veniva raccolto e poi buttato via, e ogni versione
 * pubblicata costava un giro completo di prove per scoprire una cosa che il
 * telefono sapeva gia'.
 *
 * Qui non si indovina niente. Si guardano i tre punti in cui il caricamento
 * puo' rompersi, uno per uno, e si riporta cosa e' successo davvero.
 */
data class VisionDiagnostics(
    val modelState: ModelState,
    val modelBytes: Long,
    val modelDetail: String?,
    val nativeLibraryLoaded: Boolean,
    val nativeLibraryDetail: String?,
    val pageSizeBytes: Long,
    val abis: List<String>,
    val androidRelease: String,
    val sdkInt: Int,
    val device: String,
    val lastError: String?,
) {

    enum class ModelState {
        /** Presente e apribile come descrittore di file: e' come deve stare. */
        OK,

        /** Presente ma compresso dentro l'APK: `openFd()` non lo apre. */
        COMPRESSED,

        /** Non c'e' proprio. */
        MISSING,
    }

    /**
     * true quando la libreria nativa non si carica su un telefono con pagine
     * da 16 KB.
     *
     * Dal 2025 Android gira anche con pagine di memoria da 16 KB, e una
     * libreria compilata per pagine da 4 KB semplicemente non si apre. Nessun
     * messaggio arriva all'utente: l'app resta cieca e sembra rotta a caso.
     */
    val looksLikePageSizeProblem: Boolean
        get() = !nativeLibraryLoaded && pageSizeBytes > LEGACY_PAGE_SIZE

    /**
     * La causa, in forma di codice.
     *
     * Non una frase: la frase la sceglie il livello applicazione, che sa in che
     * lingua sta parlando l'utente. Qui sotto non ci sono stringhe da tradurre.
     */
    val reason: Reason
        get() = when {
            modelState == ModelState.MISSING -> Reason.MODEL_MISSING
            modelState == ModelState.COMPRESSED -> Reason.MODEL_COMPRESSED
            looksLikePageSizeProblem -> Reason.PAGE_SIZE
            !nativeLibraryLoaded -> Reason.NATIVE_LIBRARY
            else -> Reason.UNKNOWN
        }

    enum class Reason {
        /** L'asset non e' finito nell'APK. */
        MODEL_MISSING,

        /** L'asset c'e' ma e' compresso: `openFd()` non lo apre. */
        MODEL_COMPRESSED,

        /** Libreria nativa a 4 KB su un telefono a pagine piu' grandi. */
        PAGE_SIZE,

        /** La libreria nativa non si carica, per un altro motivo. */
        NATIVE_LIBRARY,

        /** Tutto sembra a posto ma MediaPipe non parte lo stesso. */
        UNKNOWN,
    }

    /** Blocco tecnico da copiare e incollare in una segnalazione. */
    fun report(): String = buildString {
        appendLine("--- AirScroll: diagnosi riconoscimento ---")
        appendLine("modello        : $modelState, $modelBytes byte")
        modelDetail?.let { appendLine("modello (det.) : $it") }
        appendLine("libreria nativa: ${if (nativeLibraryLoaded) "caricata" else "NON caricata"}")
        nativeLibraryDetail?.let { appendLine("libreria (det.): $it") }
        appendLine("pagina memoria : $pageSizeBytes byte")
        appendLine("ABI            : ${abis.joinToString()}")
        appendLine("Android        : $androidRelease (API $sdkInt)")
        appendLine("dispositivo    : $device")
        lastError?.let { appendLine("errore         : $it") }
    }

    companion object {

        private const val LEGACY_PAGE_SIZE = 4_096L
        private const val NATIVE_LIBRARY = "mediapipe_tasks_vision_jni"

        fun collect(
            context: Context,
            modelAssetPath: String = VisionConfig.DEFAULT_MODEL_ASSET,
            lastError: String? = null,
        ): VisionDiagnostics {
            var state = ModelState.MISSING
            var bytes = 0L
            var modelDetail: String? = null

            // `openFd()` e' esattamente cio' che fa MediaPipe. Se riesce qui,
            // riuscira' anche la', e il modello e' fuori discussione.
            try {
                context.assets.openFd(modelAssetPath).use { fd ->
                    state = ModelState.OK
                    bytes = fd.declaredLength
                }
            } catch (openFdFailure: Throwable) {
                modelDetail = describe(openFdFailure)
                // Distinguere "compresso" da "assente" cambia la correzione:
                // il primo e' una riga di configurazione, il secondo e' un
                // asset che non e' mai stato impacchettato.
                try {
                    context.assets.open(modelAssetPath).use { stream ->
                        state = ModelState.COMPRESSED
                        bytes = stream.available().toLong()
                    }
                } catch (missing: Throwable) {
                    state = ModelState.MISSING
                }
            }

            var nativeLoaded = false
            var nativeDetail: String? = null
            try {
                // Idempotente: se MediaPipe l'ha gia' caricata non succede
                // nulla, se non ci riesce otteniamo il motivo per intero.
                System.loadLibrary(NATIVE_LIBRARY)
                nativeLoaded = true
            } catch (t: Throwable) {
                nativeDetail = describe(t)
            }

            val pageSize = runCatching { Os.sysconf(OsConstants._SC_PAGESIZE) }
                .getOrDefault(LEGACY_PAGE_SIZE)

            return VisionDiagnostics(
                modelState = state,
                modelBytes = bytes,
                modelDetail = modelDetail,
                nativeLibraryLoaded = nativeLoaded,
                nativeLibraryDetail = nativeDetail,
                pageSizeBytes = pageSize,
                abis = Build.SUPPORTED_ABIS.toList(),
                androidRelease = Build.VERSION.RELEASE ?: "?",
                sdkInt = Build.VERSION.SDK_INT,
                device = "${Build.MANUFACTURER} ${Build.MODEL}",
                lastError = lastError,
            )
        }

        /** Messaggio dell'eccezione piu' la catena delle cause. */
        internal fun describe(t: Throwable): String = buildString {
            var current: Throwable? = t
            var depth = 0
            while (current != null && depth < MAX_CAUSE_DEPTH) {
                if (depth > 0) append(" <- ")
                append(current::class.java.simpleName)
                current.message?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
                current = current.cause
                depth++
            }
        }

        private const val MAX_CAUSE_DEPTH = 4
    }
}
