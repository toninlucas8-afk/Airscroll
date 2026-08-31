package dev.airscroll.app.health

import android.app.ActivityManager
import android.content.Context
import androidx.core.content.getSystemService
import dev.airscroll.app.service.VisionForegroundService
import dev.airscroll.app.util.AirScrollPermissions
import dev.airscroll.core.common.runtime.AirScrollBus
import dev.airscroll.core.health.HealthSnapshot
import dev.airscroll.core.health.Problem
import dev.airscroll.core.health.diagnose
import dev.airscroll.core.settings.AirScrollSettings

/**
 * Raccoglie le condizioni vere del telefono e le passa alla diagnosi.
 *
 * Qui non c'e' nessuna decisione: le regole stanno in `:core:health`, dove si
 * possono provare. Questo file si limita a chiedere ad Android com'e' messo, e
 * ogni riga corrisponde a una domanda che il sistema sa davvero rispondere.
 */
class HealthProbe(context: Context) {

    private val appContext = context.applicationContext

    /**
     * L'ultima volta che la fotocamera ha dato errore.
     *
     * Lo scrive il servizio quando CameraX si lamenta. Non e' persistente di
     * proposito: un errore di ieri non dice niente su oggi.
     */
    @Volatile
    var lastCameraErrorAtMs: Long = 0L

    /**
     * Fino a quando l'avvio del servizio va considerato ancora in corso.
     *
     * Lo scrive chi accende l'interruttore. Senza, la prima diagnosi dopo
     * l'accensione arriverebbe mentre Android non ha ancora messo il servizio
     * nel suo elenco, e AirScroll annuncerebbe di essere morto un istante dopo
     * essere nato.
     */
    @Volatile
    private var startingUntilMs: Long = 0L

    /** Da chiamare quando si accende il servizio. */
    fun serviceStarting(nowMs: Long = System.currentTimeMillis()) {
        startingUntilMs = nowMs + START_GRACE_MS
    }

    fun snapshot(
        settings: AirScrollSettings,
        visionReady: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): HealthSnapshot {
        val permissions = AirScrollPermissions.snapshot(appContext)
        return HealthSnapshot(
            serviceEnabled = settings.serviceEnabled,
            serviceRunning = isVisionServiceRunning(),
            serviceStarting = nowMs < startingUntilMs,
            // Due domande diverse, che vanno poste entrambe: il servizio puo'
            // risultare abilitato nelle impostazioni di sistema e non essere
            // connesso al processo, per esempio subito dopo un aggiornamento.
            accessibilityConnected = permissions.accessibility &&
                AirScrollBus.accessibilityConnected.value,
            cameraPermission = permissions.camera,
            visionReady = visionReady,
            indicatorEnabled = settings.indicatorEnabled,
            canDrawOverlay = permissions.overlay,
            batteryUnrestricted = permissions.batteryUnrestricted,
            systemKills = settings.systemKills,
            rebooted = settings.rebooted,
            lastCameraErrorAtMs = lastCameraErrorAtMs,
            nowMs = nowMs,
        )
    }

    fun diagnose(
        settings: AirScrollSettings,
        visionReady: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): Problem? = diagnose(snapshot(settings, visionReady, nowMs))

    /**
     * Il foreground service e' vivo?
     *
     * `getRunningServices` e' deprecato da Android 8 perche' non mostra piu' i
     * servizi altrui: per i **propri** continua a rispondere, ed e' esattamente
     * cio' che serve qui. L'alternativa - una variabile statica messa a true
     * dal servizio - mentirebbe proprio nel caso che interessa, cioe' quando il
     * processo viene ucciso senza passare da `onDestroy`.
     */
    private fun isVisionServiceRunning(): Boolean {
        val manager = appContext.getSystemService<ActivityManager>() ?: return false
        val target = VisionForegroundService::class.java.name
        return runCatching {
            @Suppress("DEPRECATION")
            manager.getRunningServices(MAX_SERVICES)
                .any { it.service.className == target }
        }.getOrDefault(false)
    }

    private companion object {
        const val MAX_SERVICES = 64

        /** Quanto tempo si concede al servizio per comparire nell'elenco. */
        const val START_GRACE_MS = 4_000L
    }
}
