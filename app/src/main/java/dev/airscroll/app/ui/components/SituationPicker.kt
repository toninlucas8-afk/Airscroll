package dev.airscroll.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.airscroll.app.R
import dev.airscroll.core.common.model.SituationMode

/**
 * La scelta della situazione, in forma compatta.
 *
 * Sta in Home e in palestra, dove serve poterla cambiare al volo senza aprire
 * le impostazioni: la situazione e' l'unica cosa che cambia davvero da un uso
 * all'altro, e chi passa dalla cucina all'auto non vuole rimettere mano ai
 * cursori.
 *
 * Sotto ogni nome c'e' una riga che dice cosa cambia davvero, perche' un preset
 * che non spiega cosa fa e' solo un interruttore magico.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SituationPicker(
    selected: SituationMode,
    onSelect: (SituationMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SituationMode.entries.forEach { mode ->
                FilterChip(
                    selected = selected == mode,
                    onClick = { onSelect(mode) },
                    label = { Text(stringResource(situationChipLabel(mode))) },
                )
            }
        }
        Text(
            text = stringResource(situationChipBody(selected)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@androidx.annotation.StringRes
private fun situationChipLabel(mode: SituationMode): Int = when (mode) {
    SituationMode.NONE -> R.string.situation_none
    SituationMode.KITCHEN -> R.string.situation_kitchen
    SituationMode.SHOWER -> R.string.situation_shower
    SituationMode.BATHROOM -> R.string.situation_bathroom
    SituationMode.CAR -> R.string.situation_car
}

@androidx.annotation.StringRes
private fun situationChipBody(mode: SituationMode): Int = when (mode) {
    SituationMode.NONE -> R.string.situation_none_body
    SituationMode.KITCHEN -> R.string.situation_kitchen_body
    SituationMode.SHOWER -> R.string.situation_shower_body
    SituationMode.BATHROOM -> R.string.situation_bathroom_body
    SituationMode.CAR -> R.string.situation_car_body
}
