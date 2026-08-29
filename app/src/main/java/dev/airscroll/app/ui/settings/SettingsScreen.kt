package dev.airscroll.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.airscroll.app.R
import dev.airscroll.app.ui.MainViewModel
import dev.airscroll.app.ui.components.ChoiceChips
import dev.airscroll.app.ui.components.LabeledSlider
import dev.airscroll.app.ui.components.ScreenPadding
import dev.airscroll.app.ui.components.SectionCard
import dev.airscroll.app.ui.components.SwitchRow
import dev.airscroll.apps.api.AppProfileRegistry
import dev.airscroll.core.common.model.DistanceProfile
import dev.airscroll.core.common.model.HorizontalAction
import dev.airscroll.core.common.model.IndicatorCorner
import dev.airscroll.core.common.model.PerformanceMode
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var newPackage by remember { mutableStateOf("") }
    val profiles = remember(settings.customPackages) { AppProfileRegistry.all() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        SectionCard(title = stringResource(R.string.settings_movement)) {
            Text(
                text = stringResource(R.string.settings_distance),
                style = MaterialTheme.typography.bodyLarge,
            )
            ChoiceChips(
                options = DistanceProfile.entries.toList(),
                selected = settings.distanceProfile,
                label = { profile -> stringResource(distanceLabel(profile)) },
                onSelect = viewModel::setDistanceProfile,
            )
            Text(
                text = stringResource(R.string.settings_distance_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LabeledSlider(
                title = stringResource(R.string.settings_sensitivity),
                valueLabel = String.format("%.2fx", settings.sensitivity),
                value = settings.sensitivity,
                range = 0.4f..2.0f,
                onValueChange = viewModel::setSensitivity,
            )
            LabeledSlider(
                title = stringResource(R.string.settings_max_speed),
                valueLabel = "${settings.maxScrollSpeedPxPerSec.roundToInt()} px/s",
                value = settings.maxScrollSpeedPxPerSec,
                range = 600f..5000f,
                onValueChange = viewModel::setMaxScrollSpeed,
            )
            LabeledSlider(
                title = stringResource(R.string.settings_neutral_zone),
                valueLabel = String.format("%.2fx", settings.neutralZoneScale),
                value = settings.neutralZoneScale,
                range = 0.5f..3.0f,
                onValueChange = viewModel::setNeutralZoneScale,
            )
            SwitchRow(
                title = stringResource(R.string.settings_invert),
                subtitle = stringResource(R.string.settings_invert_hint),
                checked = settings.invertScroll,
                onCheckedChange = viewModel::setInvertScroll,
            )
        }

        SectionCard(title = stringResource(R.string.settings_volume)) {
            ChoiceChips(
                options = HorizontalAction.entries.toList(),
                selected = settings.horizontalAction,
                label = { action -> stringResource(horizontalLabel(action)) },
                onSelect = viewModel::setHorizontalAction,
            )
            LabeledSlider(
                title = stringResource(R.string.settings_volume_speed),
                valueLabel = String.format("%.1f /s", settings.maxVolumeStepsPerSec),
                value = settings.maxVolumeStepsPerSec,
                range = 1f..15f,
                onValueChange = viewModel::setMaxVolumeSteps,
            )
        }

        SectionCard(title = stringResource(R.string.settings_performance)) {
            ChoiceChips(
                options = PerformanceMode.entries.toList(),
                selected = settings.performanceMode,
                label = { mode -> stringResource(performanceLabel(mode)) },
                onSelect = viewModel::setPerformanceMode,
            )
            Text(
                text = stringResource(R.string.settings_performance_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = stringResource(R.string.settings_timings)) {
            LabeledSlider(
                title = stringResource(R.string.settings_waiting_window),
                valueLabel = "${settings.waitingWindowMs / 1000} s",
                value = settings.waitingWindowMs / 1000f,
                range = 2f..20f,
                steps = 17,
                onValueChange = { value -> viewModel.setWaitingWindow((value * 1000).toLong()) },
            )
            LabeledSlider(
                title = stringResource(R.string.settings_stop_hold),
                valueLabel = String.format("%.1f s", settings.stopHoldMs / 1000f),
                value = settings.stopHoldMs / 1000f,
                range = 0.5f..5f,
                onValueChange = { value -> viewModel.setStopHold((value * 1000).toLong()) },
            )
        }

        SectionCard(title = stringResource(R.string.settings_indicator)) {
            SwitchRow(
                title = stringResource(R.string.settings_indicator_show),
                subtitle = stringResource(R.string.settings_indicator_hint),
                checked = settings.indicatorEnabled,
                onCheckedChange = viewModel::setIndicatorEnabled,
            )
            ChoiceChips(
                options = IndicatorCorner.entries.toList(),
                selected = settings.indicatorCorner,
                label = { corner -> stringResource(cornerLabel(corner)) },
                onSelect = viewModel::setIndicatorCorner,
            )
            SwitchRow(
                title = stringResource(R.string.settings_haptics),
                checked = settings.hapticsEnabled,
                onCheckedChange = viewModel::setHapticsEnabled,
            )
        }

        SectionCard(title = stringResource(R.string.settings_apps)) {
            Text(
                text = stringResource(R.string.settings_apps_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            profiles.forEach { profile ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.fillMaxWidth(0.7f)) {
                        Text(profile.displayName, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = profile.packageNames.joinToString(", "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = profile.id !in settings.disabledProfileIds,
                        onCheckedChange = { enabled ->
                            viewModel.setProfileEnabled(profile.id, enabled)
                        },
                    )
                }
            }

            OutlinedTextField(
                value = newPackage,
                onValueChange = { newPackage = it },
                label = { Text(stringResource(R.string.settings_add_package)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (newPackage.isNotBlank()) {
                            viewModel.addCustomPackage(newPackage.trim())
                            newPackage = ""
                        }
                    },
                    enabled = newPackage.isNotBlank(),
                ) {
                    Text(stringResource(R.string.action_add))
                }
            }
            if (settings.customPackages.isNotEmpty()) {
                settings.customPackages.sorted().forEach { packageName ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(packageName, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { viewModel.removeCustomPackage(packageName) }) {
                            Text(stringResource(R.string.action_remove))
                        }
                    }
                }
            }
        }

        SectionCard(title = stringResource(R.string.settings_calibration)) {
            Text(
                text = if (settings.calibration.completed) {
                    stringResource(R.string.home_calibration_done)
                } else {
                    stringResource(R.string.home_calibration_missing)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { viewModel.clearCalibration() }) {
                Text(stringResource(R.string.action_reset_calibration))
            }
        }

        TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
        Spacer(Modifier.height(24.dp))
    }
}

@androidx.annotation.StringRes
private fun distanceLabel(profile: DistanceProfile): Int = when (profile) {
    DistanceProfile.NEAR -> R.string.distance_near
    DistanceProfile.MEDIUM -> R.string.distance_medium
    DistanceProfile.FAR -> R.string.distance_far
    DistanceProfile.AUTO -> R.string.distance_auto
}

@androidx.annotation.StringRes
private fun performanceLabel(mode: PerformanceMode): Int = when (mode) {
    PerformanceMode.BATTERY -> R.string.performance_battery
    PerformanceMode.BALANCED -> R.string.performance_balanced
    PerformanceMode.RESPONSIVE -> R.string.performance_responsive
}

@androidx.annotation.StringRes
private fun horizontalLabel(action: HorizontalAction): Int = when (action) {
    HorizontalAction.NONE -> R.string.horizontal_none
    HorizontalAction.VOLUME -> R.string.horizontal_volume
}

@androidx.annotation.StringRes
private fun cornerLabel(corner: IndicatorCorner): Int = when (corner) {
    IndicatorCorner.TOP_START -> R.string.corner_top_start
    IndicatorCorner.TOP_END -> R.string.corner_top_end
    IndicatorCorner.BOTTOM_START -> R.string.corner_bottom_start
    IndicatorCorner.BOTTOM_END -> R.string.corner_bottom_end
}
