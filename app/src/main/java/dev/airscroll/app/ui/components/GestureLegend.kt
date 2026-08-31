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
        ) { phase, colors -> drawThumbUpHold(phase, colors) }

        LegendEntry(
            title = stringResource(R.string.gesture_move_title),
            what = stringResource(R.string.gesture_move),
            detail = stringResource(R.string.gesture_move_info),
        ) { phase, colors -> drawVerticalMove(phase, colors) }

        LegendEntry(
            title = stringResource(R.string.gesture_sides_title),
            what = stringResource(R.string.gesture_sides),
            detail = stringResource(R.string.gesture_sides_info),
        ) { phase, colors -> drawHorizontalMove(phase, colors) }

        LegendEntry(
            title = stringResource(R.string.gesture_fist_title),
            what = stringResource(R.string.gesture_fist),
            detail = stringResource(R.string.gesture_fist_info),
        ) { phase, colors -> drawFistHold(phase, colors) }

        LegendEntry(
            title = stringResource(R.string.gesture_leave_title),
            what = stringResource(R.string.gesture_leave),
            detail = stringResource(R.string.gesture_leave_info),
        ) { phase, colors -> drawLeaveApp(phase, colors) }

        // La V sta in fondo perche' e' l'unica che richiede di aver acceso
        // qualcosa nelle impostazioni: senza i comandi vocali non fa niente, e
        // il testo lo dice.
        LegendEntry(
            title = stringResource(R.string.gesture_voice_title),
            what = stringResource(R.string.gesture_voice),
            detail = stringResource(R.string.gesture_voice_info),
        ) { phase, colors -> drawVictoryHold(phase, colors) }
    }
}

/** La V tenuta: l'arco si riempie, poi il cerchio pulsa - il microfono e' aperto. */
private fun DrawScope.drawVictoryHold(phase: Float, colors: LegendColors) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = min(size.width, size.height) / 2f * 0.92f
    val hold = (phase / 0.35f).coerceIn(0f, 1f)
    val listening = ((phase - 0.35f) / 0.65f).coerceIn(0f, 1f)

    drawCircle(color = colors.muted, radius = radius, center = center, style = Stroke(width = 4f))
    drawArc(
        color = colors.accent,
        startAngle = -90f,
        sweepAngle = 360f * hold,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = 6f),
    )
    if (hold >= 1f) {
        // L'alone che si allarga: e' il microfono aperto, e si spegne da solo -
        // come nella realta'.
        drawCircle(
            color = colors.accent.copy(alpha = 0.30f * (1f - listening)),
            radius = radius * (0.6f + 0.5f * listening),
            center = center,
        )
    }
    drawVictoryGlyph(center, radius * 0.72f, colors.accent)
}

@Composable
private fun LegendEntry(
    title: String,
    what: String,
    detail: String,
    draw: DrawScope.(phase: Float, colors: LegendColors) -> Unit,
) {
    val colors = LegendColors(
        accent = MaterialTheme.colorScheme.primary,
        muted = MaterialTheme.colorScheme.outline.copy(alpha = 0.40f),
        // Le tacche delle dita piegate si disegnano nel colore della scheda:
        // sopra un pieno dello stesso colore, un solco e' l'unico modo per
        // vederle.
        surface = MaterialTheme.colorScheme.surfaceVariant,
    )

    val transition = rememberInfiniteTransition(label = title)
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(CYCLE_MILLIS, easing = LinearEasing)),
        label = "phase",
    )

    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Canvas(Modifier.size(TILE)) { draw(phase, colors) }
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
private fun DrawScope.drawThumbUpHold(phase: Float, colors: LegendColors) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = min(size.width, size.height) / 2f * 0.92f
    val hold = ((phase - 0.15f) / 0.55f).coerceIn(0f, 1f)

    drawCircle(color = colors.muted, radius = radius, center = center, style = Stroke(width = 4f))
    drawArc(
        color = colors.accent,
        startAngle = -90f,
        sweepAngle = 360f * hold,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = 6f),
    )
    drawThumbUpGlyph(
        center = center,
        palmWidth = radius * 0.95f,
        color = if (hold >= 1f) colors.accent else colors.accent.copy(alpha = 0.75f),
        gap = colors.surface,
    )
}

/** Mano su e giu', con una scia: la pagina segue, non salta. */
private fun DrawScope.drawVerticalMove(phase: Float, colors: LegendColors) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val travel = size.height * 0.22f
    val wave = sin(phase * 2f * PI).toFloat()

    drawRoundRect(
        color = colors.muted,
        topLeft = Offset(center.x - size.width * 0.03f, size.height * 0.10f),
        size = Size(size.width * 0.06f, size.height * 0.80f),
        cornerRadius = CornerRadius(size.width * 0.03f),
    )
    drawHandGlyph(
        center = Offset(center.x, center.y - wave * travel),
        palmWidth = size.width * 0.42f,
        color = colors.accent,
    )
}

/** Mano a destra e sinistra, con le tacche del volume che si accendono. */
private fun DrawScope.drawHorizontalMove(phase: Float, colors: LegendColors) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val travel = size.width * 0.20f
    val wave = sin(phase * 2f * PI).toFloat()

    // Le tacche crescono verso destra, come una scala di volume.
    val bars = 5
    for (index in 0 until bars) {
        val height = size.height * (0.10f + 0.045f * index)
        val on = wave > -1f + 2f * (index + 0.5f) / bars
        drawRoundRect(
            color = if (on) colors.accent.copy(alpha = 0.55f) else colors.muted,
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
        color = colors.accent,
    )
}

/** Pugno chiuso tenuto: l'arco impiega volutamente quasi tutto il ciclo. */
private fun DrawScope.drawFistHold(phase: Float, colors: LegendColors) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = min(size.width, size.height) / 2f * 0.92f
    // Prima la mano e' aperta, poi si chiude e l'arco comincia a riempirsi:
    // rende visibile che i due secondi partono dalla chiusura.
    val closing = ((phase - 0.10f) / 0.12f).coerceIn(0f, 1f)
    val hold = ((phase - 0.22f) / 0.62f).coerceIn(0f, 1f)

    drawCircle(color = colors.muted, radius = radius, center = center, style = Stroke(width = 4f))
    drawArc(
        color = colors.accent,
        startAngle = -90f,
        sweepAngle = 360f * hold,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = 6f),
    )
    if (closing < 1f) {
        drawHandGlyph(center, radius * 0.95f, colors.accent.copy(alpha = 0.75f))
    } else {
        drawFistGlyph(center, radius * 0.95f, colors.accent)
    }
}

/** Uscire dall'app: la mano si allontana e il riquadro si spegne. */
private fun DrawScope.drawLeaveApp(phase: Float, colors: LegendColors) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val away = ((phase - 0.25f) / 0.5f).coerceIn(0f, 1f)

    drawRoundRect(
        color = if (away > 0.6f) colors.muted else colors.accent.copy(alpha = 0.35f),
        topLeft = Offset(size.width * 0.22f, size.height * 0.12f),
        size = Size(size.width * 0.56f, size.height * 0.76f),
        cornerRadius = CornerRadius(size.width * 0.12f),
        style = Stroke(width = 4f),
    )
    drawHandGlyph(
        center = Offset(center.x + away * size.width * 0.62f, center.y),
        palmWidth = size.width * 0.36f,
        color = colors.accent.copy(alpha = 1f - away * 0.85f),
    )
}

/** I tre colori che servono ai disegni della legenda. */
private data class LegendColors(val accent: Color, val muted: Color, val surface: Color)

private val TILE = 64.dp
private const val CYCLE_MILLIS = 3_400
