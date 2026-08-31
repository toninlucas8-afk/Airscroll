package dev.airscroll.app.ui.calibration

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.airscroll.app.R
import dev.airscroll.core.gesture.Aspect
import dev.airscroll.core.gesture.AspectScore
import dev.airscroll.core.gesture.CalibrationReport
import dev.airscroll.core.gesture.Grade

/**
 * La pagella della calibrazione, mostrata riga per riga.
 *
 * Ogni riga ha il numero misurato accanto al giudizio, di proposito: un voto
 * senza il dato dietro chiede di fidarsi, e questo progetto ha gia' dato una
 * volta un "fatto" a una misura che non valeva niente.
 */
@Composable
fun CalibrationReportCard(report: CalibrationReport, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(overallLabel(report.overall)),
            style = MaterialTheme.typography.titleMedium,
            color = colorFor(report.overall),
        )

        report.scores.forEach { score -> ScoreRow(score) }

        if (report.lopsided) {
            // Non e' un errore: e' la scoperta piu' utile della calibrazione.
            // I bracci sono fatti cosi', e saperlo prima spiega in anticipo
            // perche' un verso rispondera' diversamente dall'altro.
            Text(
                text = stringResource(R.string.calibration_report_lopsided),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScoreRow(score: AspectScore) {
    // Il voto non puo' stare solo nel colore: chi non distingue il verde dal
    // rosso - o chi usa un lettore di schermo - resterebbe senza il giudizio,
    // che e' l'unica cosa che questa riga serve a dare. La parola va accanto al
    // pallino, non al posto suo: il colore resta il modo piu' rapido di leggere
    // la pagella a colpo d'occhio.
    val voto = stringResource(gradeLabel(score.grade))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            Modifier
                .size(10.dp)
                .background(colorFor(score.grade), CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(aspectLabel(score.aspect)),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = voto,
            style = MaterialTheme.typography.labelMedium,
            color = colorFor(score.grade),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = measuredText(score),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}

/**
 * Il numero misurato, nell'unita' che si capisce.
 *
 * Le misure interne sono frazioni dell'inquadratura: dirle cosi' non
 * significherebbe niente per nessuno. In centesimi diventano numeri
 * confrontabili fra loro, che e' l'unica cosa che serve guardando una pagella.
 */
@Composable
private fun measuredText(score: AspectScore): String = when (score.aspect) {
    Aspect.GESTURES -> stringResource(
        if (score.measured >= 1f) R.string.calibration_report_gestures_ok
        else R.string.calibration_report_gestures_partial
    )

    Aspect.STILLNESS -> stringResource(
        R.string.calibration_report_value,
        (score.measured * 1000).toInt(),
    )

    else -> stringResource(R.string.calibration_report_value, (score.measured * 100).toInt())
}

@Composable
private fun colorFor(grade: Grade): Color = when (grade) {
    Grade.GOOD -> MaterialTheme.colorScheme.primary
    Grade.FAIR -> MaterialTheme.colorScheme.tertiary
    Grade.POOR -> MaterialTheme.colorScheme.error
}

/** Il voto in una parola: la stessa informazione del colore, ma leggibile. */
@StringRes
private fun gradeLabel(grade: Grade): Int = when (grade) {
    Grade.GOOD -> R.string.calibration_grade_good
    Grade.FAIR -> R.string.calibration_grade_fair
    Grade.POOR -> R.string.calibration_grade_poor
}

@StringRes
private fun overallLabel(grade: Grade): Int = when (grade) {
    Grade.GOOD -> R.string.calibration_report_good
    Grade.FAIR -> R.string.calibration_report_fair
    Grade.POOR -> R.string.calibration_report_poor
}

@StringRes
fun aspectLabel(aspect: Aspect): Int = when (aspect) {
    Aspect.FRAMING -> R.string.calibration_aspect_framing
    Aspect.STILLNESS -> R.string.calibration_aspect_stillness
    Aspect.REACH_UP -> R.string.calibration_aspect_reach_up
    Aspect.REACH_DOWN -> R.string.calibration_aspect_reach_down
    Aspect.REACH_LEFT -> R.string.calibration_aspect_reach_left
    Aspect.REACH_RIGHT -> R.string.calibration_aspect_reach_right
    Aspect.GESTURES -> R.string.calibration_aspect_gestures
}
