package dev.airscroll.app.ui.calibration

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.airscroll.app.ui.components.drawHandGlyph
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * La dimostrazione del movimento da fare, prima di doverlo fare.
 *
 * Nasce da una richiesta precisa alla prova su telefono: "deve essere spiegato
 * bene come uno deve muovere la mano per calibrare, anche con una GIF in
 * movimento per far vedere come si fa". Il movimento c'e', la GIF no: e'
 * disegnata. Una GIF sarebbe qualche megabyte in un APK che ne pesa gia'
 * sessantasette, sgranerebbe sugli schermi grandi e si porterebbe dietro i
 * suoi colori invece di quelli del tema. Qui il movimento e' identico e costa
 * zero byte.
 *
 * Due animazioni, una per passo, perche' i due gesti sono diversi e mostrarne
 * uno solo lascerebbe l'altro da indovinare.
 */
@Composable
fun CalibrationDemo(step: CalibrationStep, modifier: Modifier = Modifier) {
    when (step) {
        // L'inquadratura e la mano ferma chiedono la stessa cosa - stare fermi
        // nel posto giusto - quindi mostrano la stessa animazione.
        CalibrationStep.FRAMING, CalibrationStep.CENTER -> SteadyHandDemo(modifier)
        CalibrationStep.INTRO, CalibrationStep.RING -> CircleHandDemo(modifier)
        CalibrationStep.GESTURES, CalibrationStep.REPORT -> Unit
    }
}

/** Mano ferma al centro, con un alone che pulsa: "stai fermo qui". */
@Composable
private fun SteadyHandDemo(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    val transition = rememberInfiniteTransition(label = "fermo")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "pulse",
    )

    Canvas(modifier.fillMaxWidth().height(DEMO_HEIGHT)) {
        val center = Offset(size.width / 2f, size.height * 0.52f)
        val radius = min(size.width, size.height) * 0.30f

        // L'alone si allarga e sfuma: dice "resta qui dentro" senza scriverlo.
        val wave = (sin(pulse * 2f * PI).toFloat() + 1f) / 2f
        drawCircle(
            color = accent.copy(alpha = 0.10f + 0.10f * (1f - wave)),
            radius = radius * (0.75f + 0.25f * wave),
            center = center,
        )
        drawCircle(color = muted, radius = radius, center = center, style = Stroke(width = 3f))

        drawHandGlyph(
            center = center,
            palmWidth = radius * 0.85f,
            color = accent,
        )
    }
}

/**
 * La mano che fa il giro, e gli spicchi che si accendono al suo passaggio.
 *
 * Il giro dura cinque secondi ed e' volutamente lento: e' anche la velocita'
 * giusta da tenere. Un'animazione frettolosa insegnerebbe il gesto sbagliato.
 */
@Composable
private fun CircleHandDemo(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    val transition = rememberInfiniteTransition(label = "cerchio")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(LAP_MILLIS, easing = LinearEasing)),
        label = "phase",
    )

    Canvas(modifier.fillMaxWidth().height(DEMO_HEIGHT)) {
        val center = Offset(size.width / 2f, size.height * 0.52f)
        val radius = min(size.width, size.height) * 0.33f
        val thickness = radius * 0.16f
        val sweep = 360f / SECTORS

        // Una breve pausa al centro prima di partire, come nel gesto vero:
        // prima ci si ferma, poi si comincia il giro.
        val moving = ((phase - REST_FRACTION) / (1f - REST_FRACTION)).coerceIn(0f, 1f)
        val lit = (moving * SECTORS).toInt()

        for (index in 0 until SECTORS) {
            val on = index < lit
            drawArc(
                color = if (on) accent else muted,
                startAngle = -(index + 1) * sweep + SECTOR_GAP / 2f,
                sweepAngle = sweep - SECTOR_GAP,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = if (on) thickness else thickness * 0.55f),
            )
        }

        drawCircle(
            color = muted,
            radius = radius * 0.22f,
            center = center,
            style = Stroke(width = thickness * 0.25f),
        )

        // Durante la pausa la mano sta al centro, poi esce e percorre il giro.
        val distance = if (phase < REST_FRACTION) 0f else radius
        val angle = moving * 2f * PI.toFloat()
        val handCenter = Offset(
            x = center.x + distance * cos(angle),
            y = center.y - distance * sin(angle),
        )
        drawHandGlyph(
            center = handCenter,
            palmWidth = radius * 0.55f,
            color = accent,
        )
    }
}

private val DEMO_HEIGHT = 210.dp
private const val SECTORS = 12
private const val SECTOR_GAP = 4f
private const val LAP_MILLIS = 5_600

/** Frazione iniziale del ciclo in cui la mano resta ferma al centro. */
private const val REST_FRACTION = 0.12f
