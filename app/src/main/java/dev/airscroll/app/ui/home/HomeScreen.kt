package dev.airscroll.app.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.airscroll.app.R
import dev.airscroll.app.ui.MainViewModel
import dev.airscroll.app.ui.components.Bullet
import dev.airscroll.app.ui.components.ScreenPadding
import dev.airscroll.app.ui.components.SectionCard
import dev.airscroll.app.ui.components.StatusDot
import dev.airscroll.app.ui.components.SwitchRow
import dev.airscroll.app.util.AirScrollPermissions
import dev.airscroll.core.common.model.EngineState

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
    onOpenCalibration: () -> Unit,
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val status by viewModel.engineStatus.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissions() }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissions() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = stringResource(R.string.home_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(status.state, size = 18)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(stateLabel(status.state)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = status.activeProfileName
                            ?: stringResource(R.string.state_no_app),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SwitchRow(
                title = stringResource(R.string.home_master_switch),
                subtitle = stringResource(R.string.home_master_switch_subtitle),
                checked = settings.serviceEnabled,
                onCheckedChange = { enabled -> viewModel.setServiceEnabled(enabled) },
            )

            if (!permissions.essentialsGranted && settings.serviceEnabled) {
                Text(
                    text = stringResource(R.string.home_missing_permissions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            status.lastError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        SectionCard(title = stringResource(R.string.home_permissions_title)) {
            PermissionRow(
                label = stringResource(R.string.permission_camera),
                description = stringResource(R.string.permission_camera_why),
                granted = permissions.camera,
                actionLabel = stringResource(R.string.action_grant),
                onAction = { cameraLauncher.launch(Manifest.permission.CAMERA) },
            )
            PermissionRow(
                label = stringResource(R.string.permission_accessibility),
                description = stringResource(R.string.permission_accessibility_why),
                granted = permissions.accessibility,
                actionLabel = stringResource(R.string.action_open_settings),
                onAction = { context.startActivity(AirScrollPermissions.accessibilitySettingsIntent()) },
            )
            PermissionRow(
                label = stringResource(R.string.permission_overlay),
                description = stringResource(R.string.permission_overlay_why),
                granted = permissions.overlay,
                actionLabel = stringResource(R.string.action_open_settings),
                onAction = { context.startActivity(AirScrollPermissions.overlaySettingsIntent(context)) },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionRow(
                    label = stringResource(R.string.permission_notifications),
                    description = stringResource(R.string.permission_notifications_why),
                    granted = permissions.notifications,
                    actionLabel = stringResource(R.string.action_grant),
                    onAction = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                )
            }
            PermissionRow(
                label = stringResource(R.string.permission_battery),
                description = stringResource(R.string.permission_battery_why),
                granted = permissions.batteryUnrestricted,
                actionLabel = stringResource(R.string.action_open_settings),
                onAction = { context.startActivity(AirScrollPermissions.batteryOptimizationIntent(context)) },
            )
        }

        SectionCard(title = stringResource(R.string.home_calibration_title)) {
            Text(
                text = if (settings.calibration.completed) {
                    stringResource(R.string.home_calibration_done)
                } else {
                    stringResource(R.string.home_calibration_missing)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenCalibration) {
                Text(
                    text = if (settings.calibration.completed) {
                        stringResource(R.string.action_recalibrate)
                    } else {
                        stringResource(R.string.action_calibrate)
                    }
                )
            }
        }

        SectionCard(title = stringResource(R.string.home_gestures_title)) {
            Bullet(stringResource(R.string.gesture_thumb_up))
            Bullet(stringResource(R.string.gesture_move))
            Bullet(stringResource(R.string.gesture_sides))
            Bullet(stringResource(R.string.gesture_fist))
            Bullet(stringResource(R.string.gesture_leave))
        }

        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_settings))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PermissionRow(
    label: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.66f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (granted) {
            Text(
                text = stringResource(R.string.status_granted),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@androidx.annotation.StringRes
private fun stateLabel(state: EngineState): Int = when (state) {
    EngineState.DISABLED -> R.string.state_disabled
    EngineState.IDLE -> R.string.state_idle
    EngineState.WAITING -> R.string.state_waiting
    EngineState.ACTIVE -> R.string.state_active
}
