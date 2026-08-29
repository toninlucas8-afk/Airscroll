package dev.airscroll.core.camera

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import dev.airscroll.core.common.model.PerformanceMode

/**
 * Compatibilita' adattiva: scegliamo un profilo di default in base a quanto e'
 * capace il telefono, cosi' un dispositivo modesto non viene messo in
 * ginocchio e uno recente non viene sprecato.
 */
object DeviceCapabilities {

    fun suggestPerformanceMode(context: Context): PerformanceMode {
        val activityManager = context.getSystemService<ActivityManager>()
        val lowRam = activityManager?.isLowRamDevice == true
        val cores = Runtime.getRuntime().availableProcessors()
        val memoryClass = activityManager?.largeMemoryClass ?: 128

        return when {
            lowRam || cores <= 4 || memoryClass < 192 -> PerformanceMode.BATTERY
            cores >= 8 && memoryClass >= 384 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                PerformanceMode.RESPONSIVE
            else -> PerformanceMode.BALANCED
        }
    }
}
