package dev.airscroll.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.airscroll.app.R
import dev.airscroll.app.ui.MainViewModel
import dev.airscroll.app.ui.components.Bullet
import dev.airscroll.app.ui.components.GesturePreviewAnimation
import dev.airscroll.app.ui.components.IconRow
import dev.airscroll.app.ui.components.ScreenPadding
import dev.airscroll.app.ui.components.SectionCard
import dev.airscroll.app.ui.components.Wordmark
import dev.airscroll.app.util.AirScrollPermissions
import dev.airscroll.app.util.PermissionSnapshot

private enum class SetupStep {
    WELCOME,
    PRIVACY,
    CAMERA,
    ACCESSIBILITY,
    EXTRAS,
    CALIBRATION,
    PRACTICE,
}

/**
 * Configurazione guidata.
 *
 * Un passo alla volta, e ogni passo si sblocca solo quando quello prima e'
 * davvero fatto. E' l'opposto della vecchia schermata, che elencava quattro
 * permessi tutti insieme e lasciava l'utente a indovinare quale fosse il
 * problema quando qualcosa non funzionava.
 */
@Composable
fun GuidedSetupScreen(
    viewModel: MainViewModel,
    onOpenCalibration: () -> Unit,
    onOpenPractice: () -> Unit,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var step by remember { mutableStateOf(SetupStep.WELCOME) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissions() }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissions() }

    val steps = SetupStep.entries.toList()
    val index = steps.indexOf(step)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Wordmark()
        LinearProgressIndicator(
            progress = { (index + 1f) / steps.size },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
        )
        Text(
            text = stringResource(R.string.setup_progress, index + 1, steps.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when (step) {
            SetupStep.WELCOME -> WelcomeStep()
            SetupStep.PRIVACY -> PrivacyStep()
            SetupStep.CAMERA -> CameraStep(
                granted = permissions.camera,
                onGrant = { cameraLauncher.launch(Manifest.permission.CAMERA) },
            )
            SetupStep.ACCESSIBILITY -> AccessibilityStep(
                granted = permissions.accessibility,
                onOpenAccessibility = {
                    context.startActivity(AirScrollPermissions.accessibilitySettingsIntent())
                },
                onOpenAppInfo = {
                    context.startActivity(AirScrollPermissions.appSettingsIntent(context))
                },
            )
            SetupStep.EXTRAS -> ExtrasStep(
                permissions = permissions,
                onOverlay = { context.startActivity(AirScrollPermissions.overlaySettingsIntent(context)) },
                onNotifications = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                onBattery = { context.startActivity(AirScrollPermissions.batteryOptimizationIntent(context)) },
            )
            SetupStep.CALIBRATION -> CalibrationStep(
                done = settings.calibration.completed,
                onCalibrate = onOpenCalibration,
            )
            SetupStep.PRACTICE -> PracticeStep(onPractice = onOpenPractice)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (index > 0) {
                TextButton(onClick = { step = steps[index - 1] }) {
                    Text(stringResource(R.string.action_back))
                }
            } else {
                TextButton(onClick = onFinished) {
                    Text(stringResource(R.string.action_skip))
                }
            }
            Button(
                onClick = {
                    if (index == steps.lastIndex) onFinished() else step = steps[index + 1]
                },
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    if (index == steps.lastIndex) stringResource(R.string.setup_finish)
                    else stringResource(R.string.action_next)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun WelcomeStep() {
    GesturePreviewAnimation(
        accent = MaterialTheme.colorScheme.primary,
        ink = MaterialTheme.colorScheme.onSurface,
        muted = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SectionCard(title = stringResource(R.string.onboarding_what_title)) {
        Text(
            text = stringResource(R.string.onboarding_what_body),
            style = MaterialTheme.typography.bodyLarge,
        )
        Bullet(stringResource(R.string.onboarding_use_kitchen))
        Bullet(stringResource(R.string.onboarding_use_work))
        Bullet(stringResource(R.string.onboarding_use_gym))
        Bullet(stringResource(R.string.onboarding_use_reading))
        Bullet(stringResource(R.string.onboarding_use_accessibility))
    }
}

@Composable
private fun PrivacyStep() {
    SectionCard(title = stringResource(R.string.onboarding_privacy_title)) {
        IconRow(
            icon = Icons.Filled.Shield,
            title = stringResource(R.string.onboarding_privacy_body),
        )
        Bullet(stringResource(R.string.onboarding_privacy_offline))
        Bullet(stringResource(R.string.onboarding_privacy_no_recording))
        Bullet(stringResource(R.string.onboarding_privacy_camera_off))
        Bullet(stringResource(R.string.onboarding_privacy_no_content))
    }
}

@Composable
private fun CameraStep(granted: Boolean, onGrant: () -> Unit) {
    StepCard(
        title = stringResource(R.string.setup_camera_title),
        body = stringResource(R.string.setup_camera_body),
        done = granted,
        doneLabel = stringResource(R.string.setup_camera_done),
        actionLabel = stringResource(R.string.permission_camera_grant),
        onAction = onGrant,
    )
}

@Composable
private fun AccessibilityStep(
    granted: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenAppInfo: () -> Unit,
) {
    StepCard(
        title = stringResource(R.string.setup_accessibility_title),
        body = stringResource(R.string.setup_accessibility_body),
        done = granted,
        doneLabel = stringResource(R.string.setup_accessibility_done),
        actionLabel = stringResource(R.string.permission_accessibility_grant),
        onAction = onOpenAccessibility,
    )

    // Il blocco di Android 13+ e' la causa piu' comune di fallimento qui:
    // meglio spiegarlo prima che l'utente ci sbatta contro.
    AnimatedVisibility(visible = !granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        SectionCard(accent = MaterialTheme.colorScheme.error.copy(alpha = 0.4f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.restricted_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = stringResource(R.string.restricted_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Bullet(stringResource(R.string.restricted_step_1))
            Bullet(stringResource(R.string.restricted_step_2))
            Bullet(stringResource(R.string.restricted_step_3))
            OutlinedButton(onClick = onOpenAppInfo, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_open_app_info))
            }
        }
    }
}

@Composable
private fun ExtrasStep(
    permissions: PermissionSnapshot,
    onOverlay: () -> Unit,
    onNotifications: () -> Unit,
    onBattery: () -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.setup_extras_title),
        subtitle = stringResource(R.string.setup_extras_body),
    ) {
        ExtraRow(
            icon = { Icon(Icons.Filled.Layers, null, tint = it, modifier = Modifier.size(20.dp)) },
            title = stringResource(R.string.permission_overlay),
            body = stringResource(R.string.permission_overlay_why),
            done = permissions.overlay,
            onAction = onOverlay,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ExtraRow(
                icon = { Icon(Icons.Filled.Notifications, null, tint = it, modifier = Modifier.size(20.dp)) },
                title = stringResource(R.string.permission_notifications),
                body = stringResource(R.string.permission_notifications_why),
                done = permissions.notifications,
                onAction = onNotifications,
            )
        }
        ExtraRow(
            icon = { Icon(Icons.Filled.BatteryFull, null, tint = it, modifier = Modifier.size(20.dp)) },
            title = stringResource(R.string.permission_battery),
            body = stringResource(R.string.permission_battery_why),
            done = permissions.batteryUnrestricted,
            onAction = onBattery,
        )
    }
}

@Composable
private fun CalibrationStep(done: Boolean, onCalibrate: () -> Unit) {
    SectionCard(title = stringResource(R.string.setup_calibration_title)) {
        IconRow(
            icon = Icons.Filled.Straighten,
            title = stringResource(R.string.setup_calibration_headline),
            body = stringResource(R.string.setup_calibration_body),
        )
        Button(
            onClick = onCalibrate,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(
                if (done) stringResource(R.string.action_recalibrate)
                else stringResource(R.string.action_calibrate)
            )
        }
        if (done) {
            Text(
                text = stringResource(R.string.home_calibration_done),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PracticeStep(onPractice: () -> Unit) {
    SectionCard(title = stringResource(R.string.setup_practice_title)) {
        IconRow(
            icon = Icons.Filled.CameraAlt,
            title = stringResource(R.string.setup_practice_headline),
            body = stringResource(R.string.setup_practice_body),
        )
        Button(
            onClick = onPractice,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(stringResource(R.string.action_open_practice))
        }
    }
}

@Composable
private fun StepCard(
    title: String,
    body: String,
    done: Boolean,
    doneLabel: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    SectionCard(
        accent = if (done) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (done) Icons.Filled.CheckCircle else Icons.Filled.Accessibility,
                contentDescription = null,
                tint = if (done) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (done) {
            Text(
                text = doneLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Button(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun ExtraRow(
    icon: @Composable (androidx.compose.ui.graphics.Color) -> Unit,
    title: String,
    body: String,
    done: Boolean,
    onAction: () -> Unit,
) {
    val tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        icon(tint)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (done) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        } else {
            TextButton(onClick = onAction) { Text(stringResource(R.string.action_activate)) }
        }
    }
}
