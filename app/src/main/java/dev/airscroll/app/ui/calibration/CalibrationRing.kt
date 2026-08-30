package dev.airscroll.app.ui.calibration

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.min

/**
 * Il cerchio della calibrazione: dodici spicchi che si accendono man mano che
 * la mano raggiunge quella direzione.
 *
 * E' la parte che rende comprensibile la calibrazione senza spiegazioni. Non
 * c'e' scritto "muovi la mano di 0,15 unita' normalizzate verso l'alto": c'e'
 * uno spicchio spento, e uno lo accende andando li'. La stessa idea di Face ID,
 * per lo stesso motivo - le persone completano volentieri una cosa incompleta.
 */
@Composable
fun CalibrationRing(
    sectors: List<Boolean>,
    handOffsetX: Float,
    handOffsetY: Float,
    handVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val filledColor = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val dotColor = MaterialTheme.colorScheme.primary
    val lostColor = MaterialTheme.colorScheme.error

    // Il puntino insegue la mano con un filo di ritardo: senza, il tremolio
    // naturale lo fa vibrare e sembra un difetto invece di una mano ferma.
    val dotX by animateFloatAsState(handOffsetX, tween(90), label = "dotX")
    val dotY by animateFloatAsState(handOffsetY, tween(90), label = "dotY")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = min(size.width, size.height) / 2f * RING_FRACTION
            val thickness = radius * THICKNESS_FRACTION
            val count = sectors.size.coerceAtLeast(1)
            val sweep = 360f / count

            for (index in sectors.indices) {
                // Su schermo l'asse verticale e' rivolto in giu', mentre gli
                // spicchi sono numerati con l'asse rivolto in su, come li vede
                // l'utente: da qui il segno meno.
                val startAngle = -(index + 1) * sweep + ANGLE_OFFSET
                val on = sectors[index]
                drawArc(
                    color = if (on) filledColor else emptyColor,
                    startAngle = startAngle + SECTOR_GAP_DEGREES / 2f,
                    sweepAngle = sweep - SECTOR_GAP_DEGREES,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = if (on) thickness else thickness * 0.55f),
                )
            }

            // Il bersaglio: il cerchio interno dove la mano sta a riposo.
            drawCircle(
                color = emptyColor,
                radius = radius * REST_FRACTION,
                center = center,
                style = Stroke(width = thickness * 0.25f),
            )

            val dotCenter = Offset(
                x = center.x + dotX * radius,
                y = center.y - dotY * radius,
            )
            drawCircle(
                color = if (handVisible) dotColor else lostColor,
                radius = thickness * 0.7f,
                center = dotCenter,
            )
        }

        val done = sectors.count { it }
        Text(
            text = "$done / ${sectors.size}",
            style = MaterialTheme.typography.headlineSmall,
            color = if (done == sectors.size) filledColor else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private const val RING_FRACTION = 0.82f
private const val THICKNESS_FRACTION = 0.14f
private const val REST_FRACTION = 0.22f
private const val SECTOR_GAP_DEGREES = 4f

/** Il primo spicchio comincia a destra, come l'angolo zero della matematica. */
private const val ANGLE_OFFSET = 0f
