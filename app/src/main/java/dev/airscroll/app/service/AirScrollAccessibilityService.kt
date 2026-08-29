package dev.airscroll.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import dev.airscroll.core.common.model.ScrollCommand
import dev.airscroll.core.common.runtime.AirScrollBus
import dev.airscroll.core.control.ScrollDispatcher
import dev.airscroll.core.control.VolumeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Il braccio che tocca lo schermo.
 *
 * Fa due sole cose, e nessuna delle due richiede di leggere i contenuti delle
 * app (`canRetrieveWindowContent` e' false nel config XML):
 *  - segnala quale app e' in primo piano, guardando solo il nome del package;
 *  - esegue i gesti calcolati dal motore.
 */
class AirScrollAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var scrollDispatcher: ScrollDispatcher? = null
    private var volumeController: VolumeController? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        scrollDispatcher = ScrollDispatcher(this)
        volumeController = VolumeController(this)
        AirScrollBus.publishAccessibilityConnected(true)

        scope.launch {
            AirScrollBus.scroll.collect { command -> applyScroll(command) }
        }
        scope.launch {
            AirScrollBus.volume.collect { command -> volumeController?.apply(command) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName in IGNORED_PACKAGES) return
        if (packageName == this.packageName) return
        AirScrollBus.publishForegroundPackage(packageName)
    }

    override fun onInterrupt() {
        scrollDispatcher?.stop()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun applyScroll(command: ScrollCommand) {
        val dispatcher = scrollDispatcher ?: return
        if (command.isMoving) dispatcher.update(command) else dispatcher.stop()
    }

    private fun teardown() {
        AirScrollBus.publishAccessibilityConnected(false)
        scrollDispatcher?.release()
        scrollDispatcher = null
        volumeController = null
        scope.cancel()
    }

    private companion object {
        /**
         * La barra di sistema e le tastiere cambiano "finestra" in continuazione:
         * se le ascoltassimo, uscire dallo stato attivo sarebbe un terno al lotto.
         */
        val IGNORED_PACKAGES = setOf(
            "com.android.systemui",
            "com.google.android.inputmethod.latin",
            "com.android.inputmethod.latin",
            "android",
        )
    }
}
