package dev.airscroll.app.ui.home

import android.Manifest
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.airscroll.app.R
import dev.airscroll.app.bootstrap.ServiceLocator
import dev.airscroll.app.ui.MainViewModel
import dev.airscroll.app.ui.components.GestureLegend
import dev.airscroll.app.ui.components.IconRow
import dev.airscroll.app.ui.components.Pill
import dev.airscroll.app.ui.components.ProblemCard
import dev.airscroll.app.ui.components.ScreenPadding
import dev.airscroll.app.ui.components.SectionCard
import dev.airscroll.app.ui.components.SituationPicker
import dev.airscroll.app.ui.components.StatusDot
import dev.airscroll.app.ui.components.SwitchRow
import dev.airscroll.app.ui.components.Wordmark
import dev.airscroll.app.ui.components.colorForState
import dev.airscroll.app.util.AirScrollPermissions
import dev.airscroll.app.util.FlightRecordFiles
import dev.airscroll.app.util.PermissionSnapshot
import dev.airscroll.core.common.model.EngineState
import dev.airscroll.core.health.Problem

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
    onOpenCalibration: () -> Unit,
    onOpenPractice: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val status by viewModel.engineStatus.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val problem by viewModel.problem.collectAsStateWithLifecycle()

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissions() }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissions() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Senza questo il logotipo finisce sotto l'orologio della status bar.
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Wordmark()
        Text(
            text = stringResource(R.string.home_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))

        StatusCard(
            state = status.state,
            detail = status.activeProfileName ?: stringResource(R.string.state_no_app),
            serviceEnabled = settings.serviceEnabled,
            permissions = permissions,
            error = status.lastError,
            onToggle = viewModel::setServiceEnabled,
        )

        // Il pulsante che rende possibile tararla sui movimenti veri: invece
        // di chiedere una sessione di laboratorio *prima*, quando tutto
        // funziona, si manda il momento in cui non ha funzionato - subito dopo
        // che e' successo.
        TextButton(
            onClick = {
                val intent = FlightRecordFiles.shareIntent(context, ServiceLocator.flightRecorder)
                if (intent == null) {
                    Toast.makeText(context, R.string.flight_empty, Toast.LENGTH_LONG).show()
                } else {
                    context.startActivity(
                        Intent.createChooser(intent, context.getString(R.string.flight_send))
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.flight_send))
        }

        problem?.let { guasto ->
            ProblemCard(
                problem = guasto,
                // Il "l'ho sistemato" ha senso solo dove c'e' un conteggio da
                // azzerare: altrove il guasto sparisce da solo appena e' finito.
                onDismissWarning = if (guasto == Problem.BATTERY_RESTRICTED) {
                    viewModel::dismissKillWarning
                } else {
                    null
                },
            )
        }

        SectionCard(title = stringResource(R.string.practice_card_title)) {
            IconRow(
                icon = Icons.Filled.SportsMartialArts,
                title = stringResource(R.string.practice_card_headline),
                body = stringResource(R.string.practice_card_body),
            )
            Button(
                onClick = onOpenPractice,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(stringResource(R.string.action_open_practice))
            }
        }

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            RoundedCornerShape(12.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Restaurant,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Box(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_situation),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            SituationPicker(
                selected = settings.situationMode,
                onSelect = viewModel::setSituationMode,
            )
        }

        // Da Android 13 i permessi "sensibili" delle app installate a mano sono
        // bloccati finche' non si sbloccano esplicitamente. E' la causa numero
        // uno di "ho installato l'app e non funziona niente".
        if (!permissions.accessibility && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RestrictedSettingsCard(
                onOpenAppInfo = { context.startActivity(AirScrollPermissions.appSettingsIntent(context)) }
            )
        }

        PermissionsCard(
            permissions = permissions,
            onGrantCamera = { cameraLauncher.launch(Manifest.permission.CAMERA) },
            onGrantNotifications = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            onOpenAccessibility = { context.startActivity(AirScrollPermissions.accessibilitySettingsIntent()) },
            onOpenOverlay = { context.startActivity(AirScrollPermissions.overlaySettingsIntent(context)) },
            onOpenBattery = { context.startActivity(AirScrollPermissions.batteryOptimizationIntent(context)) },
        )

        SectionCard(title = stringResource(R.string.home_calibration_title)) {
            IconRow(
                icon = Icons.Filled.Straighten,
                title = if (settings.calibration.completed) {
                    stringResource(R.string.home_calibration_done_title)
                } else {
                    stringResource(R.string.home_calibration_missing_title)
                },
                body = if (settings.calibration.completed) {
                    stringResource(R.string.home_calibration_done)
                } else {
                    stringResource(R.string.home_calibration_missing)
                },
                tint = if (settings.calibration.completed) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            Button(
                onClick = onOpenCalibration,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    if (settings.calibration.completed) stringResource(R.string.action_recalibrate)
                    else stringResource(R.string.action_calibrate)
                )
            }
        }

        SectionCard(title = stringResource(R.string.home_legend_title)) {
            LegendRow(EngineState.IDLE, stringResource(R.string.legend_red))
            LegendRow(EngineState.WAITING, stringResource(R.string.legend_yellow))
            LegendRow(EngineState.ACTIVE, stringResource(R.string.legend_green))
        }

        // La legenda dei gesti, animata.
        //
        // Prima era un elenco di icone ferme, e le due cose che alla prima prova
        // non erano ovvie sono proprio quelle che un pittogramma non puo' dire:
        // che il pollice in su va **tenuto**, e che la pagina **segue** la mano
        // invece di saltare a scatti.
        SectionCard(
            title = stringResource(R.string.home_gestures_title),
            subtitle = stringResource(R.string.home_gestures_subtitle),
        ) {
            GestureLegend()
        }

        OutlinedButton(
            onClick = onOpenSetup,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Filled.School, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.action_replay_setup))
        }

        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.action_settings))
        }

        Text(
            text = stringResource(R.string.home_footer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun StatusCard(
    state: EngineState,
    detail: String,
    serviceEnabled: Boolean,
    permissions: PermissionSnapshot,
    error: String?,
    onToggle: (Boolean) -> Unit,
) {
    val accent = colorForState(state)
    SectionCard(accent = accent.copy(alpha = 0.35f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(state = state, size = 16.dp, withPulse = true)
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(stateLabel(state)),
                    style = MaterialTheme.typography.titleLarge,
                    color = accent,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        SwitchRow(
            title = stringResource(R.string.home_master_switch),
            subtitle = stringResource(R.string.home_master_switch_subtitle),
            checked = serviceEnabled,
            enabled = permissions.camera,
            onCheckedChange = onToggle,
        )

        if (!permissions.essentialsGranted) {
            NoticeRow(
                text = stringResource(R.string.home_missing_permissions),
                tone = MaterialTheme.colorScheme.error,
            )
        }
        error?.let { NoticeRow(text = it, tone = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun NoticeRow(text: String, tone: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = Icons.Filled.WarningAmber,
            contentDescription = null,
            tint = tone,
            modifier = Modifier
                .padding(top = 1.dp, end = 10.dp)
                .size(17.dp),
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = tone)
    }
}

@Composable
private fun PermissionsCard(
    permissions: PermissionSnapshot,
    onGrantCamera: () -> Unit,
    onGrantNotifications: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenBattery: () -> Unit,
) {
    val showNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val total = if (showNotifications) 5 else 4
    val granted = listOf(
        permissions.camera,
        permissions.accessibility,
        permissions.overlay,
        permissions.batteryUnrestricted,
    ).count { it } + if (showNotifications && permissions.notifications) 1 else 0

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.home_permissions_title).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Pill(
                text = "$granted/$total",
                tone = if (granted == total) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }

        PermissionRow(
            icon = Icons.Filled.CameraAlt,
            label = stringResource(R.string.permission_camera),
            description = stringResource(R.string.permission_camera_why),
            granted = permissions.camera,
            actionLabel = stringResource(R.string.action_grant),
            onAction = onGrantCamera,
        )
        PermissionRow(
            icon = Icons.Filled.Accessibility,
            label = stringResource(R.string.permission_accessibility),
            description = stringResource(R.string.permission_accessibility_why),
            granted = permissions.accessibility,
            actionLabel = stringResource(R.string.action_activate),
            onAction = onOpenAccessibility,
        )
        PermissionRow(
            icon = Icons.Filled.Layers,
            label = stringResource(R.string.permission_overlay),
            description = stringResource(R.string.permission_overlay_why),
            granted = permissions.overlay,
            actionLabel = stringResource(R.string.action_activate),
            onAction = onOpenOverlay,
        )
        if (showNotifications) {
            PermissionRow(
                icon = Icons.Filled.Notifications,
                label = stringResource(R.string.permission_notifications),
                description = stringResource(R.string.permission_notifications_why),
                granted = permissions.notifications,
                actionLabel = stringResource(R.string.action_grant),
                onAction = onGrantNotifications,
            )
        }
        PermissionRow(
            icon = Icons.Filled.BatteryFull,
            label = stringResource(R.string.permission_battery),
            description = stringResource(R.string.permission_battery_why),
            granted = permissions.batteryUnrestricted,
            actionLabel = stringResource(R.string.action_activate),
            onAction = onOpenBattery,
        )
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    label: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .padding(end = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (granted) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = stringResource(R.string.status_granted),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        } else {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/**
 * Spiega il blocco che Android 13+ mette ai permessi sensibili delle app
 * installate fuori dagli store, e come toglierlo.
 */
@Composable
private fun RestrictedSettingsCard(onOpenAppInfo: () -> Unit) {
    val tone = MaterialTheme.colorScheme.error
    SectionCard(accent = tone.copy(alpha = 0.4f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = tone,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.restricted_title),
                style = MaterialTheme.typography.titleMedium,
                color = tone,
            )
        }
        Text(
            text = stringResource(R.string.restricted_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NumberedStep(1, stringResource(R.string.restricted_step_1))
        NumberedStep(2, stringResource(R.string.restricted_step_2))
        NumberedStep(3, stringResource(R.string.restricted_step_3))
        Button(
            onClick = onOpenAppInfo,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(stringResource(R.string.action_open_app_info))
        }
        Text(
            text = stringResource(R.string.restricted_play_protect),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NumberedStep(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    RoundedCornerShape(999.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun LegendRow(state: EngineState, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(state = state, size = 12.dp)
        Spacer(Modifier.width(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@androidx.annotation.StringRes
private fun stateLabel(state: EngineState): Int = when (state) {
    EngineState.DISABLED -> R.string.state_disabled
    EngineState.IDLE -> R.string.state_idle
    EngineState.WAITING -> R.string.state_waiting
    EngineState.ACTIVE -> R.string.state_active
}
