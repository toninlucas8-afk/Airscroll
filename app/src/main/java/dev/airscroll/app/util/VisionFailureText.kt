package dev.airscroll.app.util

import android.content.Context
import dev.airscroll.app.R
import dev.airscroll.core.vision.VisionDiagnostics

/**
 * Traduce la diagnosi tecnica in una frase per l'utente.
 *
 * Sta nel modulo applicazione e non in `:core:vision` perche' qui ci sono le
 * risorse, e quindi le cinque lingue. Il modulo di visione resta senza testi.
 */
fun visionFailureHeadline(context: Context, diagnostics: VisionDiagnostics?): String {
    if (diagnostics == null) return context.getString(R.string.error_vision_unknown)
    return when (diagnostics.reason) {
        VisionDiagnostics.Reason.MODEL_MISSING ->
            context.getString(R.string.error_vision_model_missing)
        VisionDiagnostics.Reason.MODEL_COMPRESSED ->
            context.getString(R.string.error_vision_model_compressed)
        VisionDiagnostics.Reason.PAGE_SIZE ->
            context.getString(R.string.error_vision_page_size, diagnostics.pageSizeBytes / 1024)
        VisionDiagnostics.Reason.NATIVE_LIBRARY ->
            context.getString(R.string.error_vision_native_library)
        VisionDiagnostics.Reason.UNKNOWN ->
            context.getString(R.string.error_vision_unknown)
    }
}
