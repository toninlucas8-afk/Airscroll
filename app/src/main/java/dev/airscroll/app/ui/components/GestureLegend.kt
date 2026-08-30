package dev.airscroll.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.airscroll.app.R
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * I gesti che si muovono.
 *
 * La legenda c'era gia', ma era un elenco di icone ferme: un pittogramma di
 * pollice in su non dice che va **tenuto**, e una freccia su e giu' non dice
 * che la pagina **segue** la mano invece di saltare a scatti. Sono proprio le
 * due cose che alla prima prova non erano ovvie.
 *
 * Ogni riga si anima da sola, in ciclo, e accanto porta il dettaglio che di
 * solito resta nella documentazione che nessuno legge: quanto va tenuto un
 * gesto, cosa succede se la mano trema, perche' non si cambia volume per
 * sbaglio mentre si scorre.
 *
 * Disegnate e non filmate: pesano zero nell'APK, restano nitide e prendono i
 * colori del tema.
 */
@Composable
fun GestureLegend(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        LegendEntry(
            title = stringResource(R.string.gesture_thumb_up_title),
            what = stringResource(R.string.gesture_thumb_up),
            detail = stringResource(R.string.gesture_thumb_up_info),
        ) { phase, accent, muted -> drawThumbUpHold(phase, accent, muted) }

        LegendEntry(
            title = stringResource(R.string.gesture_move_title),
            what = stringResource(R.string.gesture_move),
            detail = stringResource(R.string.gesture_move_info),
        ) { phase, accent, muted -> drawVerticalMove(phase, accent, muted) }

        LegendEntry(
            title = stringResource(R.string.gesture_sides_title),
            what = stringResource(R.string.gesture_sides),
            detail = stringResource(R.string.gesture_sides_info),
        ) { phase, accent, muted -> drawHorizontalMove(phase, accent, muted) }

        LegendEntry(
            title = stringResource(R.string.gesture_fist_title),
            what = stringResource(R.string.gesture_fist),
            detail = stringResource(R.string.gesture_fist_info),
        ) { phase, accent, muted -> drawFistHold(phase, accent, muted) }

        LegendEntry(
            title = stringResource(R.string.gesture_leave_title),
            what = stringResource(R.string.gesture_leave),
            detail = stringResource(R.string.gesture_leave_info),
        ) { phase, accent, muted -> drawLeaveApp(phase, accent, muted) }
    }
}

@Composable
private fun LegendEntry(
    title: String,
    what: String,
    detail: String,
    draw: DrawScope.(phase: Float, accent: Color, muted: Color) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.outline.copy(alpha = 0.40f)

    val transition = rememberInfiniteTransition(label = title)
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(CYCLE_MILLIS, easing = LinearEasing)),
        label = "phase",
    )

    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Canvas(Modifier.size(TILE)) { draw(phase, accent, muted) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = what,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            )
        }
    }
}

// --- I cinque disegni -------------------------------------------------------

/** Pollice in su, con l'arco che si riempie: il gesto va **tenuto**. */
private fun DrawScope.drawThumbUpHold(phase: Float, accent: Color, muted: Color) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = min(size.width, size.height) / 2f * 0.92f
    val hold = ((phase - 0.15f) / 0.55f).coerceIn(0f, 1f)

    drawCircle(color = muted, radius = radius, center = center, style = Stroke(width = 4f))
    drawArc(
        color = accent,
        startAngle = -90f,
        sweepAngle = 360f * hold,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = 6f),
    )
    drawThumbUpGlyph(center, radius * 0.95f, if (hold >= 1f) accent else accent.copy(alpha = 0.75f))
}

/** Mano su e giu', con una scia: la pagina segue, non salta. */
private fun DrawScope.drawVerticalMove(phase: Float, accent: Color, muted: Color) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val travel = size.height * 0.22f
    val wave = sin(phase * 2f * PI).toFloat()

    drawRoundRect(
        color = muted,
        topLeft = Offset(center.x - size.width * 0.03f, size.height * 0.10f),
        size = Size(size.width * 0.06f, size.height * 0.80f),
        cornerRadius = CornerRadius(size.width * 0.03f),
    )
    drawHandGlyph(
        center = Offset(center.x, center.y - wave * travel),
        palmWidth = size.width * 0.42f,
        color = accent,
    )
}

/** Mano a destra e sinistra, con le tacche del volume che si accendono. */
private fun DrawScope.drawHorizontalMove(phase: Float, accent: Color, muted: Color) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val travel = size.width * 0.20f
    val wave = sin(phase * 2f * PI).toFloat()

    // Le tacche crescono verso destra, come una scala di volume.
    val bars = 5
    for (index in 0 until bars) {
        val height = size.height * (0.10f + 0.045f * index)
        val on = wave > -1f + 2f * (index + 0.5f) / bars
        drawRoundRect(
            color = if (on) accent.copy(alpha = 0.55f) else muted,
            topLeft = Offset(
                size.width * (0.10f + 0.18f * index),
                size.height * 0.86f - height,
            ),
            size = Size(size.width * 0.07f, height),
            cornerRadius = CornerRadius(size.width * 0.035f),
        )
    }
    drawHandGlyph(
        center = Offset(center.x + wave * travel, center.y - size.height * 0.12f),
        palmWidth = size.width * 0.40f,
        color = accent,
    )
}

/** Pugno chiuso tenuto: l'arco impiega volutamente quasi tutto il ciclo. */
private fun DrawScope.drawFistHold(phase: Float, accent: Color, muted: Color) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = min(size.width, size.height) / 2f * 0.92f
    // Prima la mano e' aperta, poi si chiude e l'arco comincia a riempirsi:
    // rende visibile che i due secondi partono dalla chiusura.
    val closing = ((phase - 0.10f) / 0.12f).coerceIn(0f, 1f)
    val hold = ((phase - 0.22f) / 0.62f).coerceIn(0f, 1f)

    drawCircle(color = muted, radius = radius, center = center, style = Stroke(width = 4f))
    drawArc(
        color = accent,
        startAngle = -90f,
        sweepAngle = 360f * hold,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = 6f),
    )
    if (closing < 1f) {
        drawHandGlyph(center, radius * 0.95f, accent.copy(alpha = 0.75f))
    } else {
        drawFistGlyph(center, radius * 0.95f, accent)
    }
}

/** Uscire dall'app: la mano si allontana e il riquadro si spegne. */
private fun DrawScope.drawLeaveApp(phase: Float, accent: Color, muted: Color) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val away = ((phase - 0.25f) / 0.5f).coerceIn(0f, 1f)

    drawRoundRect(
        color = if (away > 0.6f) muted else accent.copy(alpha = 0.35f),
        topLeft = Offset(size.width * 0.22f, size.height * 0.12f),
        size = Size(size.width * 0.56f, size.height * 0.76f),
        cornerRadius = CornerRadius(size.width * 0.12f),
        style = Stroke(width = 4f),
    )
    drawHandGlyph(
        center = Offset(center.x + away * size.width * 0.62f, center.y),
        palmWidth = size.width * 0.36f,
        color = accent.copy(alpha = 1f - away * 0.85f),
    )
}

private val TILE = 64.dp
private const val CYCLE_MILLIS = 3_400
