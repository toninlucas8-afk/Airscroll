package dev.airscroll.app.ui.lab

import android.content.Intent
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.airscroll.app.R
import dev.airscroll.app.ui.components.Pill
import dev.airscroll.app.ui.components.ScreenPadding
import dev.airscroll.app.ui.components.SectionCard
import dev.airscroll.app.util.AirScrollPermissions
import java.io.File

/**
 * Laboratorio: registra come questa mano appare al modello.
 *
 * Cinque prese guidate, poi un file CSV che l'utente puo' aprire, leggere e
 * decidere se condividere. Nel file non c'e' nessuna immagine.
 */
@Composable
fun LabScreen(
    onBack: () -> Unit,
    viewModel: LabViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hasCamera = remember { AirScrollPermissions.hasCamera(context) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            scaleX = -1f
        }
    }

    DisposableEffect(hasCamera) {
        if (hasCamera) {
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            viewModel.start(lifecycleOwner, preview)
        }
        onDispose { viewModel.stop() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.lab_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.lab_privacy),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!hasCamera) {
            SectionCard { Text(stringResource(R.string.calibration_needs_camera)) }
            TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            return@Column
        }

        state.error?.let { error ->
            SectionCard(accent = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(18.dp))
                .border(
                    width = 2.dp,
                    color = if (state.recording) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    shape = RoundedCornerShape(18.dp),
                )
        ) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(
                text = if (state.handPresent) {
                    stringResource(R.string.lab_hand_seen)
                } else {
                    stringResource(R.string.lab_hand_missing)
                },
                tone = if (state.handPresent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            Pill(text = "${state.signal.name} ${(state.confidence * 100).toInt()}%")
        }

        if (state.finished) {
            FinishedCard(
                file = state.savedFile,
                onShare = { file -> shareRecording(context, file) },
                onRestart = viewModel::restartAll,
                onBack = onBack,
            )
        } else {
            SectionCard(
                title = stringResource(
                    R.string.lab_take_progress,
                    state.takeIndex + 1,
                    LabTake.entries.size,
                ),
            ) {
                Text(
                    text = stringResource(state.take.prompt),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(state.take.hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (state.recording) {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                    )
                    Text(
                        text = stringResource(R.string.lab_recording, state.framesInTake),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Button(
                        onClick = viewModel::startTake,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(stringResource(R.string.lab_start_take))
                    }
                }
            }
        }

        TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FinishedCard(
    file: File?,
    onShare: (File) -> Unit,
    onRestart: () -> Unit,
    onBack: () -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.lab_done_title),
        accent = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
    ) {
        Text(
            text = stringResource(R.string.lab_done_body),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (file != null) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.lab_done_size, file.length() / 1024),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { onShare(file) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(stringResource(R.string.lab_share))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onRestart, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.lab_restart))
            }
            Spacer(Modifier.width(4.dp))
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_back))
            }
        }
    }
}

/**
 * Condivide il CSV con l'app che l'utente preferisce.
 *
 * Passa da un FileProvider perche' Android non permette di consegnare un
 * percorso di file grezzo a un'altra app: si concede il permesso di lettura
 * per quel singolo file e per quella singola volta.
 */
private fun shareRecording(context: android.content.Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.recordings", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.lab_share)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
}
