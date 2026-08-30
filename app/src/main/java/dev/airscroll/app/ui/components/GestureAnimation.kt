package dev.airscroll.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * L'illustrazione del primo avvio: una mano che sale e scende, e il contenuto
 * del telefono che la segue.
 *
 * E' disegnata, non un video: pesa zero nell'APK, e' nitida su qualunque
 * schermo e prende i colori del tema. Un video di due secondi sarebbe costato
 * qualche megabyte a un pacchetto che ne pesa gia' 46.
 */
@Composable
fun GesturePreviewAnimation(
    modifier: Modifier = Modifier,
    accent: Color,
    ink: Color,
    muted: Color,
) {
    val transition = rememberInfiniteTransition(label = "intro")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
        ),
        label = "phase",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(190.dp)
    ) {
        val h = size.height
        val w = size.width

        // Un unico movimento sinusoidale guida sia la mano sia il contenuto:
        // e' proprio il punto, il contenuto non fa altro che seguire.
        val wave = sin(phase * 2f * Math.PI).toFloat()

        val phoneWidth = h * 0.46f
        val phoneHeight = h * 0.86f
        val phoneLeft = w * 0.5f - phoneWidth - h * 0.10f
        val phoneTop = (h - phoneHeight) / 2f

        // Corpo del telefono
        drawRoundRect(
            color = muted,
            topLeft = Offset(phoneLeft, phoneTop),
            size = Size(phoneWidth, phoneHeight),
            cornerRadius = CornerRadius(h * 0.09f),
            style = Stroke(width = h * 0.014f),
        )

        // Righe di contenuto che scorrono dentro lo schermo
        val padding = phoneWidth * 0.14f
        val screenLeft = phoneLeft + padding
        val screenTop = phoneTop + padding
        val screenWidth = phoneWidth - padding * 2f
        val screenHeight = phoneHeight - padding * 2f
        val lineGap = screenHeight / 6.5f
        val offset = wave * lineGap * 1.6f

        clipRect(
            left = screenLeft,
            top = screenTop,
            right = screenLeft + screenWidth,
            bottom = screenTop + screenHeight,
        ) {
            for (index in -2..9) {
                val y = screenTop + index * lineGap + offset
                val isTitle = ((index % 4) + 4) % 4 == 0
                drawRoundRect(
                    color = if (isTitle) accent else ink.copy(alpha = 0.35f),
                    topLeft = Offset(screenLeft, y),
                    size = Size(
                        width = if (isTitle) screenWidth * 0.62f else screenWidth * 0.92f,
                        height = lineGap * (if (isTitle) 0.30f else 0.18f),
                    ),
                    cornerRadius = CornerRadius(lineGap * 0.1f),
                )
            }
        }

        // La mano, a destra del telefono, che sale e scende in sincrono.
        val handCenterX = w * 0.5f + h * 0.24f
        val handCenterY = h * 0.5f - wave * h * 0.17f
        drawHandGlyph(
            center = Offset(handCenterX, handCenterY),
            palmWidth = h * 0.30f,
            color = accent,
        )

        // Scia verticale: suggerisce l'asse del movimento.
        drawRoundRect(
            color = accent.copy(alpha = 0.16f),
            topLeft = Offset(handCenterX - h * 0.012f, h * 0.16f),
            size = Size(h * 0.024f, h * 0.68f),
            cornerRadius = CornerRadius(h * 0.012f),
        )
    }
}
