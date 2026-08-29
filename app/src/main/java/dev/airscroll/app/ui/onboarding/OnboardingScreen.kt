package dev.airscroll.app.ui.onboarding

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import dev.airscroll.app.util.AirScrollPermissions

/**
 * Tre passi: cosa fa, cosa NON fa (privacy), e i permessi.
 *
 * Chiedere i permessi senza aver prima spiegato perche' e' il modo piu' rapido
 * per farseli negare.
 */
@Composable
fun OnboardingScreen(
    viewModel: MainViewModel,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    var step by remember { mutableIntStateOf(0) }

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

        when (step) {
            0 -> SectionCard(title = stringResource(R.string.onboarding_what_title)) {
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

            1 -> SectionCard(title = stringResource(R.string.onboarding_privacy_title)) {
                Text(
                    text = stringResource(R.string.onboarding_privacy_body),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Bullet(stringResource(R.string.onboarding_privacy_offline))
                Bullet(stringResource(R.string.onboarding_privacy_no_recording))
                Bullet(stringResource(R.string.onboarding_privacy_camera_off))
                Bullet(stringResource(R.string.onboarding_privacy_no_content))
            }

            else -> SectionCard(title = stringResource(R.string.onboarding_permissions_title)) {
                Text(
                    text = stringResource(R.string.onboarding_permissions_body),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(
                    onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) },
                    enabled = !permissions.camera,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (permissions.camera) stringResource(R.string.permission_camera_ok)
                        else stringResource(R.string.permission_camera_grant)
                    )
                }
                Button(
                    onClick = {
                        context.startActivity(AirScrollPermissions.accessibilitySettingsIntent())
                    },
                    enabled = !permissions.accessibility,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (permissions.accessibility) stringResource(R.string.permission_accessibility_ok)
                        else stringResource(R.string.permission_accessibility_grant)
                    )
                }
                Button(
                    onClick = {
                        context.startActivity(AirScrollPermissions.overlaySettingsIntent(context))
                    },
                    enabled = !permissions.overlay,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (permissions.overlay) stringResource(R.string.permission_overlay_ok)
                        else stringResource(R.string.permission_overlay_grant)
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Button(
                        onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        enabled = !permissions.notifications,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (permissions.notifications) stringResource(R.string.permission_notifications_ok)
                            else stringResource(R.string.permission_notifications_grant)
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.onboarding_permissions_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onFinished) {
                Text(stringResource(R.string.action_skip))
            }
            Button(onClick = { if (step >= 2) onFinished() else step++ }) {
                Text(
                    if (step >= 2) stringResource(R.string.action_calibrate)
                    else stringResource(R.string.action_next)
                )
            }
        }
    }
}
