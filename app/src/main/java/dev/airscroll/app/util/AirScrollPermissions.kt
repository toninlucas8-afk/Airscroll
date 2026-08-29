package dev.airscroll.app.util

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import dev.airscroll.app.service.AirScrollAccessibilityService

/** Stato di tutti i permessi che AirScroll puo' chiedere. */
data class PermissionSnapshot(
    val camera: Boolean = false,
    val notifications: Boolean = false,
    val overlay: Boolean = false,
    val accessibility: Boolean = false,
    val batteryUnrestricted: Boolean = false,
) {
    /** Il minimo indispensabile per far girare il motore. */
    val essentialsGranted: Boolean get() = camera && accessibility
}

object AirScrollPermissions {

    fun snapshot(context: Context) = PermissionSnapshot(
        camera = hasCamera(context),
        notifications = hasNotifications(context),
        overlay = Settings.canDrawOverlays(context),
        accessibility = isAccessibilityEnabled(context),
        batteryUnrestricted = isIgnoringBatteryOptimizations(context),
    )

    fun hasCamera(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    fun hasNotifications(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService<PowerManager>()
            ?.isIgnoringBatteryOptimizations(context.packageName) ?: false

    /**
     * Legge l'elenco di sistema dei servizi di accessibilita' attivi.
     *
     * Non esiste un'API diretta per "il mio servizio e' acceso?": questa e' la
     * strada che usano tutte le app del genere.
     */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, AirScrollAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        if (enabled.isEmpty()) return false
        return enabled.split(':').any { entry ->
            val component = ComponentName.unflattenFromString(entry)
            component == expected ||
                (component?.packageName == expected.packageName &&
                    component.className == expected.className)
        }
    }

    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun overlaySettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )

    fun batteryOptimizationIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )

    fun appSettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        )
}
