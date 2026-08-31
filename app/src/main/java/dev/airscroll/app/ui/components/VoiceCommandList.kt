package dev.airscroll.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.airscroll.app.R
import dev.airscroll.core.voice.VoicePhrasebook

/**
 * L'elenco completo di cio' che si puo' dire, dentro l'app.
 *
 * Le frasi non sono scritte qui: arrivano da [VoicePhrasebook], lo stesso posto
 * da cui nasce la tabella del manuale, e un test verifica che ognuna faccia
 * davvero quello che promette. Cosi' questa schermata non puo' invecchiare per
 * conto suo mentre il vocabolario cambia.
 *
 * Serve a rispondere alla domanda che chiunque si fa davanti a un microfono:
 * *cosa ascolta, esattamente?* La risposta e' questa lista, e nient'altro.
 */
@Composable
fun VoiceCommandList(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.voice_commands_language_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        VoicePhrasebook.Group.entries.forEach { gruppo ->
            val frasi = VoicePhrasebook.of(gruppo)
            if (frasi.isEmpty()) return@forEach

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(groupLabel(gruppo)).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                frasi.forEach { frase ->
                    Text(
                        text = "“${frase.canonical}”",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@StringRes
private fun groupLabel(group: VoicePhrasebook.Group): Int = when (group) {
    VoicePhrasebook.Group.SPOTIFY -> R.string.voice_group_spotify
    VoicePhrasebook.Group.ALTRE_APP -> R.string.voice_group_apps
    VoicePhrasebook.Group.LETTORE -> R.string.voice_group_player
    VoicePhrasebook.Group.VOLUME -> R.string.voice_group_volume
    VoicePhrasebook.Group.AIRSCROLL -> R.string.voice_group_airscroll
}
