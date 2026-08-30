package dev.airscroll.app.ui.practice

import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.airscroll.app.R
import dev.airscroll.app.ui.components.LabeledSlider
import dev.airscroll.app.ui.components.Pill
import dev.airscroll.app.ui.components.SectionCard
import dev.airscroll.app.ui.components.StatusDot
import dev.airscroll.app.ui.components.SwitchRow
import dev.airscroll.app.ui.components.VisionFailureCard
import dev.airscroll.app.ui.components.colorForState
import dev.airscroll.app.util.AirScrollPermissions
import dev.airscroll.core.common.model.EngineState
import dev.airscroll.core.common.model.HandSignal
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Schermata di prova: contenuto finto che scorre davvero con la mano.
 *
 * Non serve il servizio di accessibilita', quindi si puo' usare da subito, anche
 * mentre Android tiene bloccati i permessi sensibili.
 */
@Composable
fun PracticeScreen(
    onBack: () -> Unit,
    viewModel: PracticeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
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

    val contentScroll = rememberScrollState()

    // Il ciclo che trasforma la velocita' del motore in scorrimento reale.
    // Un fotogramma alla volta, cosi' il movimento e' continuo come quello che
    // il servizio produce nelle altre app.
    LaunchedEffect(hasCamera) {
        var previousFrame = 0L
        while (isActive) {
            val now = withFrameNanos { it }
            val deltaSeconds = if (previousFrame == 0L) 0f else (now - previousFrame) / 1_000_000_000f
            previousFrame = now
            val velocity = viewModel.velocity.value
            if (velocity != 0f && deltaSeconds > 0f) {
                // Velocita' positiva = il dito scende = si torna indietro nel testo.
                contentScroll.scrollBy(-velocity * deltaSeconds)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(
                text = stringResource(R.string.practice_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            StatusDot(state = state.engineState, size = 14.dp, withPulse = true)
            Spacer(Modifier.width(14.dp))
        }

        if (!hasCamera) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.calibration_needs_camera))
            }
            return@Column
        }

        state.error?.let { error ->
            VisionFailureCard(
                headline = error,
                report = state.diagnostics,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        LiveStrip(
            engineState = state.engineState,
            signal = state.signal,
            handPresent = state.handPresent,
            velocity = state.velocityPxPerSec,
            previewView = previewView,
            onRearm = viewModel::arm,
        )

        // Il contenuto finto: e' qui che si vede se lo scorrimento e' piacevole.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(contentScroll)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.practice_article_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            PracticeParagraphs()
            PracticeGallery()
            Spacer(Modifier.height(40.dp))
        }

        DiagnosticsRow(state)

        TuningBar(
            sensitivity = settings.sensitivity,
            neutralZone = settings.neutralZoneScale,
            kitchenMode = settings.kitchenMode,
            onSensitivity = viewModel::setSensitivity,
            onNeutralZone = viewModel::setNeutralZone,
            onKitchenMode = viewModel::setKitchenMode,
        )
    }
}

@Composable
private fun LiveStrip(
    engineState: EngineState,
    signal: HandSignal,
    handPresent: Boolean,
    velocity: Float,
    previewView: PreviewView,
    onRearm: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(76.dp)
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, colorForState(engineState), RoundedCornerShape(14.dp))
        ) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(
                    when (engineState) {
                        EngineState.ACTIVE -> R.string.practice_state_active
                        EngineState.WAITING -> R.string.practice_state_waiting
                        else -> R.string.practice_state_idle
                    }
                ),
                style = MaterialTheme.typography.titleMedium,
                color = colorForState(engineState),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(
                    text = stringResource(signalLabel(signal)),
                    tone = if (handPresent) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (abs(velocity) > 0f) {
                    Pill(text = "${velocity.roundToInt()} px/s")
                }
            }
            if (engineState != EngineState.ACTIVE) {
                TextButton(onClick = onRearm) {
                    Text(stringResource(R.string.practice_rearm))
                }
            }
        }
    }
}

/**
 * Una riga di numeri che vale mille segnalazioni.
 *
 * Se il riconoscimento non funziona, questa riga dice subito da che parte
 * guardare: nessun fotogramma inviato significa fotocamera ferma, fotogrammi
 * inviati senza risultati significa modello che non gira, risultati che
 * arrivano senza gesti significa che e' davvero questione di luce o posa.
 */
@Composable
private fun DiagnosticsRow(state: PracticeUiState) {
    val stats = state.stats
    val stalled = stats.submitted > 20 && stats.results == 0L
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(
                R.string.practice_diagnostics,
                if (stats.usingGpu) "GPU" else "CPU",
                stats.submitted,
                stats.results,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (stalled) {
            Text(
                text = stringResource(R.string.practice_diagnostics_stalled),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun TuningBar(
    sensitivity: Float,
    neutralZone: Float,
    kitchenMode: Boolean,
    onSensitivity: (Float) -> Unit,
    onNeutralZone: (Float) -> Unit,
    onKitchenMode: (Boolean) -> Unit,
) {
    SectionCard(modifier = Modifier.padding(16.dp)) {
        Text(
            text = stringResource(R.string.practice_tuning_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LabeledSlider(
            title = stringResource(R.string.settings_sensitivity),
            valueLabel = String.format("%.2f×", sensitivity),
            value = sensitivity,
            range = 0.4f..2.0f,
            onValueChange = onSensitivity,
        )
        LabeledSlider(
            title = stringResource(R.string.settings_neutral_zone),
            valueLabel = String.format("%.2f×", neutralZone),
            value = neutralZone,
            range = 0.5f..3.0f,
            onValueChange = onNeutralZone,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Restaurant,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                SwitchRow(
                    title = stringResource(R.string.kitchen_mode_title),
                    subtitle = stringResource(R.string.kitchen_mode_body),
                    checked = kitchenMode,
                    onCheckedChange = onKitchenMode,
                )
            }
        }
    }
}

@Composable
private fun PracticeParagraphs() {
    val paragraphs = listOf(
        R.string.practice_p1,
        R.string.practice_p2,
        R.string.practice_p3,
        R.string.practice_p4,
        R.string.practice_p5,
        R.string.practice_p6,
    )
    paragraphs.forEach { paragraph ->
        Text(
            text = stringResource(paragraph),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Galleria finta: gradienti disegnati, cosi' non servono immagini nell'APK. */
@Composable
private fun PracticeGallery() {
    Text(
        text = stringResource(R.string.practice_gallery_title),
        style = MaterialTheme.typography.titleLarge,
    )
    val palettes = listOf(
        listOf(Color(0xFF2DE39A), Color(0xFF1B7A55)),
        listOf(Color(0xFF4ADE80), Color(0xFF116149)),
        listOf(Color(0xFFF2B33D), Color(0xFF8A5A12)),
        listOf(Color(0xFF5AA9E6), Color(0xFF17456F)),
        listOf(Color(0xFFE0483B), Color(0xFF6E1C16)),
        listOf(Color(0xFFB07CE8), Color(0xFF432066)),
    )
    palettes.forEach { palette ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(palette))
        )
    }
}

@androidx.annotation.StringRes
private fun signalLabel(signal: HandSignal): Int = when (signal) {
    HandSignal.THUMB_UP -> R.string.signal_thumb_up
    HandSignal.CLOSED_FIST -> R.string.signal_fist
    HandSignal.OPEN_PALM -> R.string.signal_open_palm
    HandSignal.VICTORY -> R.string.signal_victory
    HandSignal.POINTING_UP -> R.string.signal_pointing
    HandSignal.THUMB_DOWN -> R.string.signal_thumb_down
    HandSignal.LOVE -> R.string.signal_love
    HandSignal.NONE -> R.string.signal_none
}
