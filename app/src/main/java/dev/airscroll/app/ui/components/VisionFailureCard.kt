package dev.airscroll.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.airscroll.app.R

/**
 * Il riquadro rosso quando il riconoscimento non parte.
 *
 * Prima diceva sempre "manca il modello MediaPipe", che era una diagnosi
 * inventata: la causa vera veniva raccolta dall'eccezione e poi buttata via.
 * Adesso mostra quello che il telefono ha realmente risposto e lo rende
 * copiabile, perche' e' l'unica informazione che permette di correggere invece
 * di tentare.
 */
@Composable
fun VisionFailureCard(
    headline: String,
    report: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    SectionCard(
        modifier = modifier,
        accent = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
    ) {
        Text(
            text = headline,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        if (!report.isNullOrBlank()) {
            Text(
                text = report.trim(),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { copyToClipboard(context, report) }) {
                    Text(stringResource(R.string.action_copy_report))
                }
                OutlinedButton(onClick = { shareReport(context, report) }) {
                    Text(stringResource(R.string.action_share_report))
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, report: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("AirScroll", report))
}

private fun shareReport(context: Context, report: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, report)
    }
    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.action_share_report))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
