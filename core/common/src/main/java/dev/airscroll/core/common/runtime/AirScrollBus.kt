package dev.airscroll.core.common.runtime

import dev.airscroll.core.common.model.EngineStatus
import dev.airscroll.core.common.model.ScrollCommand
import dev.airscroll.core.common.model.VolumeCommand
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Canale di comunicazione fra i due servizi di AirScroll.
 *
 * Vivono nello stesso processo, quindi un singleton in memoria e' il modo piu'
 * leggero: nessun Binder, nessun broadcast, nessuna serializzazione.
 *
 * - [VisionForegroundService] produce [status], [scroll] e [volume].
 * - [AirScrollAccessibilityService] produce [foregroundPackage] e consuma i comandi.
 */
object AirScrollBus {

    private val _status = MutableStateFlow(EngineStatus())
    val status: StateFlow<EngineStatus> = _status.asStateFlow()

    private val _scroll = MutableStateFlow(ScrollCommand.Stopped)
    val scroll: StateFlow<ScrollCommand> = _scroll.asStateFlow()

    private val _volume = MutableSharedFlow<VolumeCommand>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val volume: SharedFlow<VolumeCommand> = _volume.asSharedFlow()

    /** Package dell'app attualmente in primo piano, o null se sconosciuto. */
    private val _foregroundPackage = MutableStateFlow<String?>(null)
    val foregroundPackage: StateFlow<String?> = _foregroundPackage.asStateFlow()

    /** true quando l'AccessibilityService e' connesso e puo' eseguire gesti. */
    private val _accessibilityConnected = MutableStateFlow(false)
    val accessibilityConnected: StateFlow<Boolean> = _accessibilityConnected.asStateFlow()

    fun publishStatus(status: EngineStatus) {
        _status.value = status
    }

    fun publishScroll(command: ScrollCommand) {
        _scroll.value = command
    }

    fun publishVolume(command: VolumeCommand) {
        _volume.tryEmit(command)
    }

    fun publishForegroundPackage(packageName: String?) {
        _foregroundPackage.value = packageName
    }

    fun publishAccessibilityConnected(connected: Boolean) {
        _accessibilityConnected.value = connected
        if (!connected) _scroll.value = ScrollCommand.Stopped
    }

    /** Reset completo: usato quando l'utente spegne il servizio. */
    fun reset() {
        _scroll.value = ScrollCommand.Stopped
        _status.value = EngineStatus()
    }
}
