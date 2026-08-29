package dev.airscroll.app.ui.calibration

import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.airscroll.app.R
import dev.airscroll.app.ui.components.ScreenPadding
import dev.airscroll.app.ui.components.SectionCard
import dev.airscroll.app.util.AirScrollPermissions

@Composable
fun CalibrationScreen(
    onDone: () -> Unit,
    onCancel: () -> Unit,
    viewModel: CalibrationViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hasCamera = remember { AirScrollPermissions.hasCamera(context) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // Anteprima specchiata: e' come guardarsi allo specchio, altrimenti
            // muovere la mano a destra la fa andare a sinistra sullo schermo.
            scaleX = -1f
        }
    }

    DisposableEffect(hasCamera) {
        if (hasCamera) {
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            viewModel.startCamera(lifecycleOwner, preview)
        }
        onDispose { viewModel.stopCamera() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.calibration_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        if (!hasCamera) {
            SectionCard {
                Text(stringResource(R.string.calibration_needs_camera))
                Button(onClick = { context.startActivity(AirScrollPermissions.appSettingsIntent(context)) }) {
                    Text(stringResource(R.string.action_open_settings))
                }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.action_back)) }
            }
            return@Column
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(20.dp))
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize(),
            )
        }

        SectionCard(title = stringResource(stepTitle(state.step))) {
            Text(
                text = stringResource(stepBody(state.step)),
                style = MaterialTheme.typography.bodyLarge,
            )

            if (state.recording) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = if (state.handVisible) {
                        stringResource(R.string.calibration_hand_visible)
                    } else {
                        stringResource(R.string.calibration_show_hand)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.handVisible) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }

            state.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            when {
                state.step == CalibrationStep.DONE -> {
                    Text(
                        text = stringResource(
                            R.string.calibration_summary,
                            (state.result.referenceHandSpan * 100).toInt(),
                            (state.result.verticalRange * 100).toInt(),
                            (state.result.tremor * 1000).toInt(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.save(onDone) }) {
                            Text(stringResource(R.string.action_save))
                        }
                        OutlinedButton(onClick = { viewModel.restart() }) {
                            Text(stringResource(R.string.action_redo))
                        }
                    }
                }

                !state.recording -> {
                    Button(
                        onClick = { viewModel.beginNextStep() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (state.step == CalibrationStep.INTRO) {
                                stringResource(R.string.action_start)
                            } else {
                                stringResource(R.string.action_next)
                            }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_back)) }
            TextButton(onClick = { viewModel.useDefaults(onDone) }) {
                Text(stringResource(R.string.calibration_use_defaults))
            }
        }
    }
}

@androidx.annotation.StringRes
private fun stepTitle(step: CalibrationStep): Int = when (step) {
    CalibrationStep.INTRO -> R.string.calibration_intro_title
    CalibrationStep.DISTANCE -> R.string.calibration_distance_title
    CalibrationStep.STILL -> R.string.calibration_still_title
    CalibrationStep.VERTICAL -> R.string.calibration_vertical_title
    CalibrationStep.HORIZONTAL -> R.string.calibration_horizontal_title
    CalibrationStep.DONE -> R.string.calibration_done_title
}

@androidx.annotation.StringRes
private fun stepBody(step: CalibrationStep): Int = when (step) {
    CalibrationStep.INTRO -> R.string.calibration_intro_body
    CalibrationStep.DISTANCE -> R.string.calibration_distance_body
    CalibrationStep.STILL -> R.string.calibration_still_body
    CalibrationStep.VERTICAL -> R.string.calibration_vertical_body
    CalibrationStep.HORIZONTAL -> R.string.calibration_horizontal_body
    CalibrationStep.DONE -> R.string.calibration_done_body
}
