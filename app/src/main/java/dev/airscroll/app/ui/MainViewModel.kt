package dev.airscroll.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.airscroll.app.bootstrap.ServiceLocator
import dev.airscroll.app.service.VisionForegroundService
import dev.airscroll.app.util.AirScrollPermissions
import dev.airscroll.app.util.PermissionSnapshot
import dev.airscroll.core.common.model.DistanceProfile
import dev.airscroll.core.common.model.EngineStatus
import dev.airscroll.core.common.model.HorizontalAction
import dev.airscroll.core.common.model.IndicatorCorner
import dev.airscroll.core.common.model.ScrollMode
import dev.airscroll.core.common.model.PerformanceMode
import dev.airscroll.core.common.runtime.AirScrollBus
import dev.airscroll.core.settings.AirScrollSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ServiceLocator.settings(application)

    val settings: StateFlow<AirScrollSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AirScrollSettings.Default,
    )

    val engineStatus: StateFlow<EngineStatus> = AirScrollBus.status

    private val _permissions = MutableStateFlow(PermissionSnapshot())
    val permissions: StateFlow<PermissionSnapshot> = _permissions.asStateFlow()

    fun refreshPermissions() {
        _permissions.value = AirScrollPermissions.snapshot(getApplication())
    }

    /**
     * Accende o spegne il motore.
     *
     * Il servizio va avviato mentre l'app e' in primo piano: e' la condizione
     * che Android impone per lasciargli usare la fotocamera anche dopo, quando
     * l'utente sara' dentro un'altra app.
     */
    fun setServiceEnabled(enabled: Boolean) {
        val context = getApplication<Application>()
        refreshPermissions()
        if (enabled && !AirScrollPermissions.hasCamera(context)) {
            // Senza permesso fotocamera, su Android 14 `startForeground` con tipo
            // `camera` viene rifiutato dal sistema: meglio non accendere affatto
            // l'interruttore che lasciarlo acceso su un servizio morto.
            return
        }
        viewModelScope.launch {
            repository.setServiceEnabled(enabled)
            if (enabled) VisionForegroundService.start(context)
            else VisionForegroundService.stop(context)
        }
    }

    fun setDistanceProfile(profile: DistanceProfile) = launchEdit { repository.setDistanceProfile(profile) }
    fun setPerformanceMode(mode: PerformanceMode) = launchEdit { repository.setPerformanceMode(mode) }
    fun setSensitivity(value: Float) = launchEdit { repository.setSensitivity(value) }
    fun setMaxScrollSpeed(value: Float) = launchEdit { repository.setMaxScrollSpeed(value) }
    fun setNeutralZoneScale(value: Float) = launchEdit { repository.setNeutralZoneScale(value) }
    fun setInvertScroll(value: Boolean) = launchEdit { repository.setInvertScroll(value) }
    fun setKitchenMode(value: Boolean) = launchEdit { repository.setKitchenMode(value) }
    fun setHorizontalAction(action: HorizontalAction) = launchEdit { repository.setHorizontalAction(action) }
    fun setMaxVolumeSteps(value: Float) = launchEdit { repository.setMaxVolumeStepsPerSec(value) }
    fun setIndicatorEnabled(value: Boolean) = launchEdit { repository.setIndicatorEnabled(value) }
    fun setScrollMode(mode: ScrollMode) = launchEdit { repository.setScrollMode(mode) }

    fun setIndicatorCorner(corner: IndicatorCorner) = launchEdit { repository.setIndicatorCorner(corner) }
    fun setHapticsEnabled(value: Boolean) = launchEdit { repository.setHapticsEnabled(value) }
    fun setWaitingWindow(ms: Long) = launchEdit { repository.setWaitingWindowMs(ms) }
    fun setStopHold(ms: Long) = launchEdit { repository.setStopHoldMs(ms) }
    fun setProfileEnabled(id: String, enabled: Boolean) = launchEdit { repository.setProfileEnabled(id, enabled) }
    fun addCustomPackage(name: String) = launchEdit { repository.addCustomPackage(name) }
    fun removeCustomPackage(name: String) = launchEdit { repository.removeCustomPackage(name) }
    fun completeOnboarding() = launchEdit { repository.setOnboardingCompleted(true) }
    fun clearCalibration() = launchEdit { repository.clearCalibration() }

    private fun launchEdit(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
