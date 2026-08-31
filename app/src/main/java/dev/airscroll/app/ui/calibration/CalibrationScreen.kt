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
import dev.airscroll.app.ui.components.VisionFailureCard
import dev.airscroll.app.util.AirScrollPermissions
import dev.airscroll.core.gesture.FramingHint

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

            // Il movimento si mostra prima di chiederlo. Durante il cerchio
            // vero l'animazione sparisce: due cerchi insieme, uno finto e uno
            // vivo, si darebbero solo fastidio.
            if (state.step == CalibrationStep.INTRO ||
                state.step == CalibrationStep.CENTER ||
                state.step == CalibrationStep.FRAMING
            ) {
                CalibrationDemo(step = state.step)
            }

            // L'inquadratura parla in continuazione, ed e' l'unico passo in
            // cui il consiglio cambia da un istante all'altro: avvicinati,
            // allontanati, portati al centro. Un messaggio solo che dice
            // "non va bene" non direbbe cosa fare.
            if (state.step == CalibrationStep.FRAMING) {
                Text(
                    text = stringResource(framingHintText(state.framing)),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (state.framing == FramingHint.OK) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                )
                if (state.framingStuck) {
                    // Se su questo telefono, con questa luce, le condizioni non
                    // si raggiungono mai, non si tiene nessuno fermo davanti a
                    // un passo che non finisce: si prosegue, e sara' la pagella
                    // a dire che la distanza non era buona.
                    OutlinedButton(
                        onClick = { viewModel.beginNextStep() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.calibration_framing_anyway))
                    }
                }
            }

            if (state.step == CalibrationStep.GESTURES) {
                Text(
                    text = stringResource(gestureText(state.gestureStage)),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (state.gestureStuck) {
                    // Puo' essere il gesto, puo' essere la luce, puo' essere
                    // questa mano. In ogni caso non si resta bloccati qui: si
                    // va avanti, e la pagella dira' che quel gesto non e'
                    // stato riconosciuto invece di fingere di si'.
                    Text(
                        text = stringResource(R.string.calibration_gesture_stuck),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = viewModel::skipGesture,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.action_skip))
                    }
                }
            }

            if (state.step == CalibrationStep.INTRO) {
                Text(
                    text = stringResource(R.string.calibration_tips_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        R.string.calibration_tip_open_hand,
                        R.string.calibration_tip_forearm,
                        R.string.calibration_tip_slow,
                        R.string.calibration_tip_comfort,
                        R.string.calibration_tip_frame,
                    ).forEach { tip ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("\u2022", color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = stringResource(tip),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (state.step == CalibrationStep.RING) {
                CalibrationRing(
                    sectors = state.sectors,
                    handOffsetX = state.handOffsetX,
                    handOffsetY = state.handOffsetY,
                    handVisible = state.handVisible,
                )
                if (state.sectorsLeft > 0) {
                    Text(
                        text = stringResource(R.string.calibration_ring_left, state.sectorsLeft),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.canFinishEarly) {
                    // Chi non riesce a chiudere il cerchio non deve restare
                    // bloccato: una calibrazione parziale vale piu' di una
                    // calibrazione impossibile da finire.
                    OutlinedButton(
                        onClick = { viewModel.beginNextStep() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.calibration_accept_partial))
                    }
                }
            }

            if (state.recording) {
                if (state.step != CalibrationStep.RING) {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
                VisionFailureCard(headline = error, report = state.diagnostics)
            }

            when {
                state.step == CalibrationStep.REPORT -> {
                    state.report?.let { report ->
                        CalibrationReportCard(report)
                    }

                    Button(
                        onClick = { viewModel.save(onDone) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.action_save))
                    }

                    // Il pezzo debole si rifa' da solo. Senza questo, l'unica
                    // scelta davanti a una misura storta sarebbe rifare tutto
                    // da capo - cosa che nessuno fa, e quindi la misura storta
                    // resterebbe li'.
                    state.report?.weakest?.let { debole ->
                        OutlinedButton(
                            onClick = { viewModel.redo(debole.aspect) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(
                                    R.string.calibration_redo_aspect,
                                    stringResource(aspectLabel(debole.aspect)),
                                )
                            )
                        }
                    }

                    TextButton(
                        onClick = { viewModel.restart() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.action_redo))
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
    CalibrationStep.FRAMING -> R.string.calibration_framing_title
    CalibrationStep.CENTER -> R.string.calibration_center_title
    CalibrationStep.RING -> R.string.calibration_ring_title
    CalibrationStep.GESTURES -> R.string.calibration_gestures_title
    CalibrationStep.REPORT -> R.string.calibration_done_title
}

@androidx.annotation.StringRes
private fun stepBody(step: CalibrationStep): Int = when (step) {
    CalibrationStep.INTRO -> R.string.calibration_intro_body
    CalibrationStep.FRAMING -> R.string.calibration_framing_body
    CalibrationStep.CENTER -> R.string.calibration_center_body
    CalibrationStep.RING -> R.string.calibration_ring_body
    CalibrationStep.GESTURES -> R.string.calibration_gestures_body
    CalibrationStep.REPORT -> R.string.calibration_done_body
}

/** Il consiglio del momento: cosa fare adesso, non cosa non va. */
@androidx.annotation.StringRes
private fun framingHintText(hint: FramingHint): Int = when (hint) {
    FramingHint.NO_HAND -> R.string.calibration_framing_no_hand
    FramingHint.TOO_FAR -> R.string.calibration_framing_too_far
    FramingHint.TOO_CLOSE -> R.string.calibration_framing_too_close
    FramingHint.OFF_CENTRE -> R.string.calibration_framing_off_centre
    FramingHint.OK -> R.string.calibration_framing_ok
}

@androidx.annotation.StringRes
private fun gestureText(stage: GestureStage): Int = when (stage) {
    GestureStage.THUMB -> R.string.calibration_gesture_thumb
    GestureStage.FIST -> R.string.calibration_gesture_fist
    GestureStage.DONE -> R.string.calibration_gesture_done
}
