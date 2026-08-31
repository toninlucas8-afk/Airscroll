package dev.airscroll.app.ui.power

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.airscroll.app.R
import dev.airscroll.app.ui.components.ScreenPadding
import dev.airscroll.app.ui.components.SectionCard
import kotlin.math.roundToInt

/**
 * Il misuratore di consumo.
 *
 * Esiste perche' fino alla 0.5.1 ogni affermazione sulla batteria in questo
 * progetto era un ragionamento mai verificato. Tre fasi da venti secondi, e alla
 * fine tre numeri che si possono confrontare fra loro e fra versioni diverse.
 */
@Composable
fun PowerScreen(
    onBack: () -> Unit,
    viewModel: PowerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.power_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.power_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!state.supported) {
            SectionCard(accent = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)) {
                Text(
                    text = stringResource(R.string.power_unsupported),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            return@Column
        }

        if (state.charging) {
            SectionCard(accent = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)) {
                Text(stringResource(R.string.power_charging))
            }
        }

        when (state.phase) {
            PowerPhase.INTRO -> SectionCard(title = stringResource(R.string.power_how_title)) {
                Text(
                    text = stringResource(R.string.power_how_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = { viewModel.start(lifecycleOwner) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_start))
                }
            }

            PowerPhase.DONE -> Results(state, onRestart = viewModel::restart)

            else -> SectionCard(title = stringResource(phaseTitle(state.phase))) {
                Text(
                    text = stringResource(phaseBody(state.phase)),
                    style = MaterialTheme.typography.bodyLarge,
                )
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.power_live, state.liveMilliAmps),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
    }
}

@Composable
private fun Results(state: PowerUiState, onRestart: () -> Unit) {
    val report = state.report ?: return

    SectionCard(title = stringResource(R.string.power_result_title)) {
        if (report.looksImplausible) {
            Text(
                text = stringResource(R.string.power_implausible),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(stringResource(R.string.power_row_baseline))
            Text("${(report.baselineMicroAmps / 1000f).roundToInt()} mA")
        }
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(stringResource(R.string.power_row_waiting))
            Text("${(report.waitingMicroAmps / 1000f).roundToInt()} mA")
        }
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(stringResource(R.string.power_row_active))
            Text("${(report.activeMicroAmps / 1000f).roundToInt()} mA")
        }

        Text(
            text = stringResource(
                R.string.power_cost,
                (report.waitingCostMicroAmps / 1000f).roundToInt(),
                (report.activeCostMicroAmps / 1000f).roundToInt(),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        if (state.capacityMilliAmpHours > 0) {
            Text(
                text = stringResource(
                    R.string.power_hours,
                    report.hoursOfActiveUse(state.capacityMilliAmpHours),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = stringResource(R.string.power_caveat),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(onClick = onRestart, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_redo))
        }
    }
}

@androidx.annotation.StringRes
private fun phaseTitle(phase: PowerPhase): Int = when (phase) {
    PowerPhase.BASELINE -> R.string.power_phase_baseline
    PowerPhase.WAITING -> R.string.power_phase_waiting
    PowerPhase.ACTIVE -> R.string.power_phase_active
    else -> R.string.power_how_title
}

@androidx.annotation.StringRes
private fun phaseBody(phase: PowerPhase): Int = when (phase) {
    PowerPhase.BASELINE -> R.string.power_phase_baseline_body
    PowerPhase.WAITING -> R.string.power_phase_waiting_body
    PowerPhase.ACTIVE -> R.string.power_phase_active_body
    else -> R.string.power_how_body
}
