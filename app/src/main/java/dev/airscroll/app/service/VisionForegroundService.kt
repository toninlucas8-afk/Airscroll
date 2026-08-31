package dev.airscroll.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.LifecycleService
import dev.airscroll.app.MainActivity
import dev.airscroll.app.R
import dev.airscroll.app.util.visionFailureHeadline
import dev.airscroll.app.bootstrap.ProfileResolver
import dev.airscroll.app.bootstrap.ServiceLocator
import dev.airscroll.app.health.HealthProbe
import dev.airscroll.app.health.ProblemNotifier
import dev.airscroll.core.camera.CameraController
import dev.airscroll.core.common.model.EngineState
import dev.airscroll.core.common.model.EngineStatus
import dev.airscroll.core.common.model.ScrollCommand
import dev.airscroll.core.common.model.VolumeCommand
import dev.airscroll.core.common.runtime.AirScrollBus
import dev.airscroll.core.gesture.GestureEngine
import dev.airscroll.core.health.Problem
import dev.airscroll.core.overlay.StatusOverlayController
import dev.airscroll.core.settings.AirScrollSettings
import dev.airscroll.core.settings.effective
import dev.airscroll.core.vision.MediaPipeHandTracker
import dev.airscroll.core.vision.VisionConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Il cuore che gira in background.
 *
 * Perche' un foreground service e non semplicemente l'AccessibilityService:
 * da Android 9 in poi la fotocamera e' vietata ai processi in background, e
 * l'unico modo legittimo di usarla mentre l'utente sta in un'altra app e' un
 * servizio in primo piano di tipo `camera`, avviato mentre AirScroll era
 * visibile. Da qui la notifica persistente: non e' burocrazia, e' la condizione
 * che il sistema impone per non spiare nessuno di nascosto.
 *
 * La fotocamera resta comunque chiusa in stato rosso: il servizio vive, il
 * sensore no.
 */
class VisionForegroundService : LifecycleService() {

    private lateinit var cameraController: CameraController
    private lateinit var tracker: MediaPipeHandTracker
    private lateinit var overlay: StatusOverlayController
    private lateinit var engine: GestureEngine

    private var settings: AirScrollSettings = AirScrollSettings.Default
    private var lastStatus: EngineStatus = EngineStatus()
    private var trackerStarted = false
    private var framesJob: Job? = null

    private lateinit var health: HealthProbe
    private lateinit var problems: ProblemNotifier

    /**
     * true quando siamo noi a spegnerci, perche' l'utente l'ha chiesto.
     *
     * Serve a non scambiare uno spegnimento voluto per una chiusura del
     * sistema: da fuori `onDestroy` e' identico nei due casi, e sbagliare
     * significherebbe accusare il telefono ogni volta che qualcuno preme
     * l'interruttore.
     */
    private var stoppingByUser = false

    /**
     * Uno scope che sopravvive alla morte del servizio.
     *
     * `lifecycleScope` viene annullato dentro `onDestroy`, cioe' proprio
     * quando serve scrivere che siamo stati chiusi. Questo no.
     */
    private val farewell = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        health = HealthProbe(this)
        problems = ProblemNotifier(this)
        cameraController = CameraController(this)
        overlay = StatusOverlayController(this)
        tracker = MediaPipeHandTracker(
            context = this,
            config = VisionConfig.Default.copy(preferGpu = settings.performanceMode.preferGpu),
        )
        engine = GestureEngine(EngineListener())

        startForegroundSafely(EngineStatus())
        observeSettings()
        observeForegroundApp()
        observeFrames()
        startTicker()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                stoppingByUser = true
                lifecycleScope.launch {
                    ServiceLocator.settings(this@VisionForegroundService).setServiceEnabled(false)
                }
            }

            ACTION_REARM -> engine.rearm()
        }
        // START_STICKY: se il sistema ci uccide per memoria vogliamo tornare su.
        return START_STICKY
    }

    override fun onDestroy() {
        cameraController.release()
        tracker.stop()
        overlay.hide()
        AirScrollBus.reset()
        reportIfKilled()
        super.onDestroy()
    }

    /**
     * Se non siamo stati noi a spegnerci, qualcuno ci ha chiusi.
     *
     * E' il caso piu' comune di tutti - i telefoni di molti costruttori
     * chiudono i servizi in background per risparmiare batteria - ed e'
     * anche quello che finora spariva nel silenzio: la notifica di stato se
     * ne andava insieme al servizio, e non restava niente da leggere.
     *
     * La preferenza viene riletta dal disco invece di fidarsi della copia in
     * memoria: fra la scrittura dell'utente e la nostra chiusura c'e' una
     * corsa, e perderla significherebbe accusare il telefono di una cosa
     * che ha fatto l'utente.
     */
    private fun reportIfKilled() {
        if (stoppingByUser) {
            problems.clear()
            return
        }
        val repository = ServiceLocator.settings(applicationContext)
        farewell.launch(NonCancellable) {
            val persisted = repository.settings.first()
            if (!persisted.serviceEnabled) return@launch
            repository.recordSystemKill()
            problems.update(Problem.SERVICE_KILLED)
        }
    }

    // --- osservatori -------------------------------------------------------

    private fun observeSettings() {
        lifecycleScope.launch {
            ServiceLocator.settings(this@VisionForegroundService).settings.collect { updated ->
                val previous = settings
                settings = updated

                if (!updated.serviceEnabled) {
                    stoppingByUser = true
                    stopSelf()
                    return@collect
                }

                if (previous.performanceMode != updated.performanceMode) {
                    // Cambiare delegate significa ricreare il modello: si fa solo
                    // quando serve davvero.
                    restartTracker(updated)
                }

                // `.effective` applica i preset (per esempio Modalita' Cucina)
                // senza toccare i valori scelti a mano dall'utente.
                engine.updateSettings(updated.effective)
                if (previous.disabledProfileIds != updated.disabledProfileIds ||
                    previous.customPackages != updated.customPackages
                ) {
                    val packageName = AirScrollBus.foregroundPackage.value
                    engine.onForegroundApp(packageName, ProfileResolver.resolve(packageName, updated))
                }
                syncOverlay(updated)
            }
        }
    }

    private fun observeForegroundApp() {
        lifecycleScope.launch {
            // Niente distinctUntilChanged: uno StateFlow gia' non riemette due
            // volte lo stesso valore.
            AirScrollBus.foregroundPackage.collect { packageName ->
                engine.onForegroundApp(packageName, ProfileResolver.resolve(packageName, settings))
            }
        }
    }

    private fun observeFrames() {
        framesJob?.cancel()
        val current = tracker
        framesJob = lifecycleScope.launch(Dispatchers.Main.immediate) {
            current.frames.collect { frame -> engine.onFrame(frame) }
        }
    }

    private fun startTicker() {
        lifecycleScope.launch {
            var sinceHealthCheck = 0L
            while (isActive) {
                delay(TICK_INTERVAL_MS)
                engine.tick()

                // La diagnosi non va fatta dieci volte al secondo: legge
                // impostazioni di sistema e l'elenco dei servizi, e nessuna di
                // quelle cose cambia fra un fotogramma e l'altro.
                sinceHealthCheck += TICK_INTERVAL_MS
                if (sinceHealthCheck >= HEALTH_INTERVAL_MS) {
                    sinceHealthCheck = 0L
                    checkHealth()
                }
            }
        }
    }

    /**
     * Guarda come sta AirScroll e, se qualcosa non va, lo dice.
     *
     * Il riconoscitore conta come rotto solo se e' stato avviato: a fotocamera
     * chiusa non e' rotto, e' spento, e confondere le due cose farebbe comparire
     * un guasto ogni volta che il motore torna in attesa.
     */
    private fun checkHealth() {
        val visionReady = !trackerStarted || tracker.isReady
        problems.update(health.diagnose(settings, visionReady))
    }

    // --- reazioni del motore ----------------------------------------------

    private inner class EngineListener : GestureEngine.Listener {

        override fun onStatus(status: EngineStatus) {
            lastStatus = status
            AirScrollBus.publishStatus(status)
            overlay.render(status.state, status.handPresent)
            updateNotification(status)
            updateStillnessSkip(status)
        }

        override fun onScroll(command: ScrollCommand) {
            AirScrollBus.publishScroll(command)
        }

        override fun onVolume(command: VolumeCommand) {
            AirScrollBus.publishVolume(command)
        }

        override fun onCameraNeed(needed: Boolean, targetFps: Int) {
            if (needed) openCamera(targetFps) else closeCamera()
        }

        override fun onHaptic() {
            if (!settings.hapticsEnabled) return
            vibrate()
        }
    }

    /**
     * Decide se si possono saltare i fotogrammi immobili.
     *
     * Solo in attesa, e solo finche' nessuna mano e' in vista. Nello stato
     * giallo il telefono guarda una scena che nella stragrande maggioranza dei
     * casi non cambia - un ripiano, un muro - e ogni fotogramma analizzato e'
     * corrente spesa per riconfermare che non succede niente.
     *
     * Ma appena una mano compare, il salto va spento subito: **un gesto tenuto
     * fermo e' a tutti gli effetti una scena ferma**, e saltarlo
     * significherebbe non riconoscere proprio cio' che si sta aspettando.
     */
    private fun updateStillnessSkip(status: EngineStatus) {
        // La cadenza del sensore vale in ogni stato: rallentare l'acquisizione
        // conviene sempre, non solo quando non succede niente.
        cameraController.setSensorFrameRate(settings.powerSaving)

        // Il salto dei fotogrammi immobili invece no. Solo in attesa, e solo
        // finche' nessuna mano e' in vista.
        val allowed = settings.powerSaving &&
            status.state == EngineState.WAITING &&
            !status.handPresent
        cameraController.setSkipStillFrames(allowed)
    }

    private fun openCamera(targetFps: Int) {
        if (!hasCameraPermission()) {
            engine.reportError(getString(R.string.error_camera_permission))
            return
        }
        if (!trackerStarted) {
            tracker.start()
            trackerStarted = true
            if (!tracker.isReady) {
                engine.reportError(visionFailureHeadline(this, tracker.failure))
                return
            }
        }
        if (cameraController.isBound) {
            cameraController.setTargetFps(targetFps)
            return
        }
        cameraController.bind(
            lifecycleOwner = this,
            mode = settings.performanceMode,
            targetFps = targetFps,
            onError = { error ->
                // Serve alla diagnosi per distinguere "un'altra app ha preso la
                // fotocamera" da un guasto vero e permanente.
                health.lastCameraErrorAtMs = System.currentTimeMillis()
                engine.reportError(error.message)
            },
            onFrame = ::onCameraFrame,
        )
    }

    private fun closeCamera() {
        cameraController.unbind()
        if (trackerStarted) {
            tracker.stop()
            trackerStarted = false
        }
    }

    private fun onCameraFrame(bitmap: Bitmap, timestampMs: Long) {
        tracker.submit(bitmap, timestampMs)
    }

    private fun restartTracker(updated: AirScrollSettings) {
        val wasRunning = trackerStarted
        tracker.stop()
        trackerStarted = false
        tracker = MediaPipeHandTracker(
            context = this,
            config = VisionConfig.Default.copy(preferGpu = updated.performanceMode.preferGpu),
        )
        observeFrames()
        if (wasRunning) {
            tracker.start()
            trackerStarted = true
        }
    }

    private fun syncOverlay(updated: AirScrollSettings) {
        if (updated.indicatorEnabled && overlay.canDraw) {
            overlay.show(updated.indicatorCorner)
            overlay.render(lastStatus.state, lastStatus.handPresent)
        } else {
            overlay.hide()
        }
    }

    // --- notifica ----------------------------------------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }

    private fun startForegroundSafely(status: EngineStatus) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(status), type)
        }.onFailure { error ->
            Log.e(TAG, "startForeground rifiutata", error)
            stopSelf()
        }
    }

    private fun updateNotification(status: EngineStatus) {
        runCatching {
            getSystemService<NotificationManager>()
                ?.notify(NOTIFICATION_ID, buildNotification(status))
        }
    }

    private fun buildNotification(status: EngineStatus): Notification {
        val stateText = when (status.state) {
            EngineState.DISABLED -> getString(R.string.state_disabled)
            EngineState.IDLE -> getString(R.string.state_idle)
            EngineState.WAITING -> getString(R.string.state_waiting)
            EngineState.ACTIVE -> getString(R.string.state_active)
        }
        val detail = status.activeProfileName ?: getString(R.string.state_no_app)

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, VisionForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val rearm = PendingIntent.getService(
            this,
            2,
            Intent(this, VisionForegroundService::class.java).setAction(ACTION_REARM),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_status)
            .setContentTitle(getString(R.string.notification_title, stateText))
            .setContentText(detail)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.action_rearm), rearm)
            .addAction(0, getString(R.string.action_stop), stop)
            .build()
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService<Vibrator>()
        } ?: return
        runCatching {
            vibrator.vibrate(VibrationEffect.createOneShot(HAPTIC_MS, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    companion object {
        private const val TAG = "AirScroll/Service"
        private const val CHANNEL_ID = "airscroll_status"
        private const val NOTIFICATION_ID = 1001
        private const val TICK_INTERVAL_MS = 100L

        /** Ogni quanto si controlla lo stato di salute. */
        private const val HEALTH_INTERVAL_MS = 2_000L
        private const val HAPTIC_MS = 22L

        const val ACTION_STOP = "dev.airscroll.action.STOP"
        const val ACTION_REARM = "dev.airscroll.action.REARM"

        fun start(context: Context) {
            val intent = Intent(context, VisionForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VisionForegroundService::class.java))
        }
    }
}
