package dev.airscroll.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.airscroll.core.common.model.DistanceProfile
import dev.airscroll.core.common.model.HorizontalAction
import dev.airscroll.core.common.model.IndicatorCorner
import dev.airscroll.core.common.model.PerformanceMode
import dev.airscroll.core.common.model.ScrollMode
import dev.airscroll.core.common.model.SituationMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.airScrollDataStore: DataStore<Preferences> by preferencesDataStore(name = "airscroll")

/**
 * Unica sorgente di verita' per le preferenze. Tutto locale, niente rete.
 */
class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.airScrollDataStore

    val settings: Flow<AirScrollSettings> = dataStore.data
        .catch { error ->
            // Un file corrotto non deve impedire l'avvio: si riparte dai default.
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { it.toSettings() }

    suspend fun setServiceEnabled(enabled: Boolean) = edit { it[Keys.SERVICE_ENABLED] = enabled }

    /**
     * Segna che il sistema ha chiuso il servizio da solo.
     *
     * Si somma invece di sovrascrivere: un telefono che lo fa dieci volte al
     * giorno e' un caso diverso da uno che l'ha fatto una volta sola, e la
     * differenza va vista.
     */
    suspend fun recordSystemKill() = edit { prefs ->
        prefs[Keys.SYSTEM_KILLS] = (prefs[Keys.SYSTEM_KILLS] ?: 0) + 1
    }

    /** Azzera il conteggio: si usa quando l'utente ha sistemato la causa. */
    suspend fun clearSystemKills() = edit { it[Keys.SYSTEM_KILLS] = 0 }

    suspend fun setOnboardingCompleted(completed: Boolean) =
        edit { it[Keys.ONBOARDING_COMPLETED] = completed }

    suspend fun setDistanceProfile(profile: DistanceProfile) =
        edit { it[Keys.DISTANCE_PROFILE] = profile.name }

    suspend fun setPerformanceMode(mode: PerformanceMode) =
        edit { it[Keys.PERFORMANCE_MODE] = mode.name }

    suspend fun setSensitivity(value: Float) =
        edit { it[Keys.SENSITIVITY] = value.coerceIn(0.4f, 2.0f) }

    suspend fun setMaxScrollSpeed(value: Float) =
        edit { it[Keys.MAX_SCROLL_SPEED] = value.coerceIn(600f, 5000f) }

    suspend fun setNeutralZoneScale(value: Float) =
        edit { it[Keys.NEUTRAL_ZONE_SCALE] = value.coerceIn(0.5f, 3.0f) }

    suspend fun setInvertScroll(value: Boolean) = edit { it[Keys.INVERT_SCROLL] = value }

    suspend fun setSituationMode(mode: SituationMode) = edit { it[Keys.SITUATION_MODE] = mode.name }

    suspend fun setHorizontalAction(action: HorizontalAction) =
        edit { it[Keys.HORIZONTAL_ACTION] = action.name }

    suspend fun setMaxVolumeStepsPerSec(value: Float) =
        edit { it[Keys.MAX_VOLUME_STEPS] = value.coerceIn(1f, 15f) }

    suspend fun setScrollMode(mode: ScrollMode) = edit { it[Keys.SCROLL_MODE] = mode.name }

    suspend fun setPowerSaving(value: Boolean) = edit { it[Keys.POWER_SAVING] = value }

    suspend fun setIndicatorEnabled(value: Boolean) = edit { it[Keys.INDICATOR_ENABLED] = value }

    suspend fun setIndicatorCorner(corner: IndicatorCorner) =
        edit { it[Keys.INDICATOR_CORNER] = corner.name }

    suspend fun setHapticsEnabled(value: Boolean) = edit { it[Keys.HAPTICS_ENABLED] = value }

    suspend fun setWaitingWindowMs(value: Long) =
        edit { it[Keys.WAITING_WINDOW_MS] = value.coerceIn(2_000L, 20_000L) }

    suspend fun setStopHoldMs(value: Long) =
        edit { it[Keys.STOP_HOLD_MS] = value.coerceIn(500L, 5_000L) }

    suspend fun setProfileEnabled(profileId: String, enabled: Boolean) = edit { prefs ->
        val current = prefs[Keys.DISABLED_PROFILES].orEmpty().toMutableSet()
        if (enabled) current.remove(profileId) else current.add(profileId)
        prefs[Keys.DISABLED_PROFILES] = current
    }

    suspend fun addCustomPackage(packageName: String) = edit { prefs ->
        val current = prefs[Keys.CUSTOM_PACKAGES].orEmpty().toMutableSet()
        current.add(packageName.trim())
        prefs[Keys.CUSTOM_PACKAGES] = current
    }

    suspend fun removeCustomPackage(packageName: String) = edit { prefs ->
        val current = prefs[Keys.CUSTOM_PACKAGES].orEmpty().toMutableSet()
        current.remove(packageName)
        prefs[Keys.CUSTOM_PACKAGES] = current
    }

    suspend fun saveCalibration(profile: CalibrationProfile) = edit { prefs ->
        prefs[Keys.CAL_DONE] = profile.completed
        prefs[Keys.CAL_HAND_SPAN] = profile.referenceHandSpan
        prefs[Keys.CAL_VERTICAL] = profile.verticalRange
        prefs[Keys.CAL_HORIZONTAL] = profile.horizontalRange
        prefs[Keys.CAL_REACH_UP] = profile.reachUp
        prefs[Keys.CAL_REACH_DOWN] = profile.reachDown
        prefs[Keys.CAL_REACH_LEFT] = profile.reachLeft
        prefs[Keys.CAL_REACH_RIGHT] = profile.reachRight
        prefs[Keys.CAL_TREMOR] = profile.tremor
        prefs[Keys.CAL_AT] = profile.calibratedAtMillis
    }

    suspend fun clearCalibration() = saveCalibration(CalibrationProfile.Default)

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        dataStore.edit { preferences -> block(preferences) }
    }

    private fun Preferences.toSettings(): AirScrollSettings {
        val defaults = AirScrollSettings.Default
        return AirScrollSettings(
            serviceEnabled = this[Keys.SERVICE_ENABLED] ?: defaults.serviceEnabled,
            onboardingCompleted = this[Keys.ONBOARDING_COMPLETED] ?: defaults.onboardingCompleted,
            systemKills = this[Keys.SYSTEM_KILLS] ?: defaults.systemKills,
            scrollMode = enumOrDefault(this[Keys.SCROLL_MODE], defaults.scrollMode),
            distanceProfile = enumOrDefault(this[Keys.DISTANCE_PROFILE], defaults.distanceProfile),
            performanceMode = enumOrDefault(this[Keys.PERFORMANCE_MODE], defaults.performanceMode),
            sensitivity = this[Keys.SENSITIVITY] ?: defaults.sensitivity,
            maxScrollSpeedPxPerSec = this[Keys.MAX_SCROLL_SPEED] ?: defaults.maxScrollSpeedPxPerSec,
            neutralZoneScale = this[Keys.NEUTRAL_ZONE_SCALE] ?: defaults.neutralZoneScale,
            invertScroll = this[Keys.INVERT_SCROLL] ?: defaults.invertScroll,
            // Migrazione: chi aveva la Modalita' Cucina accesa se la ritrova
            // come preset, invece di perderla in silenzio.
            situationMode = enumOrDefault(
                this[Keys.SITUATION_MODE],
                if (this[Keys.KITCHEN_MODE] == true) SituationMode.KITCHEN else defaults.situationMode,
            ),
            horizontalAction = enumOrDefault(this[Keys.HORIZONTAL_ACTION], defaults.horizontalAction),
            maxVolumeStepsPerSec = this[Keys.MAX_VOLUME_STEPS] ?: defaults.maxVolumeStepsPerSec,
            powerSaving = this[Keys.POWER_SAVING] ?: defaults.powerSaving,
            indicatorEnabled = this[Keys.INDICATOR_ENABLED] ?: defaults.indicatorEnabled,
            indicatorCorner = enumOrDefault(this[Keys.INDICATOR_CORNER], defaults.indicatorCorner),
            hapticsEnabled = this[Keys.HAPTICS_ENABLED] ?: defaults.hapticsEnabled,
            waitingWindowMs = this[Keys.WAITING_WINDOW_MS] ?: defaults.waitingWindowMs,
            stopHoldMs = this[Keys.STOP_HOLD_MS] ?: defaults.stopHoldMs,
            activationHoldMs = defaults.activationHoldMs,
            disabledProfileIds = this[Keys.DISABLED_PROFILES] ?: defaults.disabledProfileIds,
            customPackages = this[Keys.CUSTOM_PACKAGES] ?: defaults.customPackages,
            calibration = CalibrationProfile(
                completed = this[Keys.CAL_DONE] ?: false,
                referenceHandSpan = this[Keys.CAL_HAND_SPAN] ?: CalibrationProfile.DEFAULT_HAND_SPAN,
                verticalRange = this[Keys.CAL_VERTICAL] ?: CalibrationProfile.DEFAULT_VERTICAL_RANGE,
                horizontalRange = this[Keys.CAL_HORIZONTAL] ?: CalibrationProfile.DEFAULT_HORIZONTAL_RANGE,
                // Le portate per direzione sono arrivate dopo il cerchio di
                // calibrazione. Chi ha calibrato prima non le ha: si ripiega
                // sull'ampiezza unica gia' misurata, che e' esattamente cio'
                // che l'app usava fino a ieri. Nessuno si ritrova peggio di
                // com'era, e chi rifa' la calibrazione guadagna la misura vera.
                reachUp = this[Keys.CAL_REACH_UP]
                    ?: this[Keys.CAL_VERTICAL] ?: CalibrationProfile.DEFAULT_VERTICAL_RANGE,
                reachDown = this[Keys.CAL_REACH_DOWN]
                    ?: this[Keys.CAL_VERTICAL] ?: CalibrationProfile.DEFAULT_VERTICAL_RANGE,
                reachLeft = this[Keys.CAL_REACH_LEFT]
                    ?: this[Keys.CAL_HORIZONTAL] ?: CalibrationProfile.DEFAULT_HORIZONTAL_RANGE,
                reachRight = this[Keys.CAL_REACH_RIGHT]
                    ?: this[Keys.CAL_HORIZONTAL] ?: CalibrationProfile.DEFAULT_HORIZONTAL_RANGE,
                tremor = this[Keys.CAL_TREMOR] ?: CalibrationProfile.DEFAULT_TREMOR,
                calibratedAtMillis = this[Keys.CAL_AT] ?: 0L,
            ),
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, fallback: T): T =
        raw?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    private object Keys {
        val SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val DISTANCE_PROFILE = stringPreferencesKey("distance_profile")
        val PERFORMANCE_MODE = stringPreferencesKey("performance_mode")
        val SENSITIVITY = floatPreferencesKey("sensitivity")
        val MAX_SCROLL_SPEED = floatPreferencesKey("max_scroll_speed")
        val NEUTRAL_ZONE_SCALE = floatPreferencesKey("neutral_zone_scale")
        val INVERT_SCROLL = booleanPreferencesKey("invert_scroll")
        val KITCHEN_MODE = booleanPreferencesKey("kitchen_mode")
        val SITUATION_MODE = stringPreferencesKey("situation_mode")
        val HORIZONTAL_ACTION = stringPreferencesKey("horizontal_action")
        val MAX_VOLUME_STEPS = floatPreferencesKey("max_volume_steps")
        val POWER_SAVING = booleanPreferencesKey("power_saving")
        val INDICATOR_ENABLED = booleanPreferencesKey("indicator_enabled")
        val SCROLL_MODE = stringPreferencesKey("scroll_mode")
        val INDICATOR_CORNER = stringPreferencesKey("indicator_corner")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val WAITING_WINDOW_MS = longPreferencesKey("waiting_window_ms")
        val STOP_HOLD_MS = longPreferencesKey("stop_hold_ms")
        val DISABLED_PROFILES = stringSetPreferencesKey("disabled_profiles")
        val CUSTOM_PACKAGES = stringSetPreferencesKey("custom_packages")
        val CAL_DONE = booleanPreferencesKey("cal_done")
        val CAL_HAND_SPAN = floatPreferencesKey("cal_hand_span")
        val CAL_VERTICAL = floatPreferencesKey("cal_vertical")
        val CAL_HORIZONTAL = floatPreferencesKey("cal_horizontal")
        val CAL_TREMOR = floatPreferencesKey("cal_tremor")
        val CAL_REACH_UP = floatPreferencesKey("cal_reach_up")
        val CAL_REACH_DOWN = floatPreferencesKey("cal_reach_down")
        val CAL_REACH_LEFT = floatPreferencesKey("cal_reach_left")
        val CAL_REACH_RIGHT = floatPreferencesKey("cal_reach_right")
        val CAL_AT = longPreferencesKey("cal_at")
        val SYSTEM_KILLS = intPreferencesKey("system_kills")
    }
}
