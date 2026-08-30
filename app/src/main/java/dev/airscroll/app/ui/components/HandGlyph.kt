package dev.airscroll.app.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.abs

/**
 * Una mano stilizzata, disegnata invece che filmata.
 *
 * Serve in due posti - l'illustrazione del primo avvio e la dimostrazione della
 * calibrazione - e stava scritta in uno solo. Averla in un punto solo evita che
 * le due animazioni divergano e diventino due mani diverse nella stessa app.
 *
 * Disegnata e non un video o una GIF per tre motivi concreti: pesa zero
 * nell'APK, che di megabyte ne ha gia' sessantasette; resta nitida su qualunque
 * schermo, mentre una GIF a una risoluzione fissa sgrana; e prende i colori del
 * tema invece di portarsi dietro i suoi.
 *
 * @param center centro del palmo.
 * @param palmWidth larghezza del palmo: tutto il resto e' in proporzione.
 */
fun DrawScope.drawHandGlyph(
    center: Offset,
    palmWidth: Float,
    color: Color,
) {
    val palmHeight = palmWidth
    val fingerWidth = palmWidth * 0.19f
    val fingerGap = palmWidth * 0.245f
    val palmTop = center.y - palmHeight * 0.15f

    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - palmWidth / 2f, palmTop),
        size = Size(palmWidth, palmHeight),
        cornerRadius = CornerRadius(palmWidth * 0.34f),
    )

    // Quattro dita di lunghezza diversa: l'indice e il medio piu' lunghi.
    for (finger in 0..3) {
        val length = palmHeight * (0.62f - 0.07f * abs(finger - 1.2f))
        val x = center.x - palmWidth / 2f + palmWidth * 0.08f + finger * fingerGap
        drawRoundRect(
            color = color,
            topLeft = Offset(x, palmTop - length),
            size = Size(fingerWidth, length + palmHeight * 0.2f),
            cornerRadius = CornerRadius(fingerWidth / 2f),
        )
    }

    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - palmWidth * 0.78f, center.y + palmHeight * 0.12f),
        size = Size(palmWidth * 0.42f, fingerWidth),
        cornerRadius = CornerRadius(fingerWidth / 2f),
    )
}

/**
 * Pugno chiuso: il palmo senza dita distese, con le nocche accennate.
 *
 * E' il gesto di uscita, e nella legenda deve essere riconoscibile a colpo
 * d'occhio accanto alla mano aperta: da qui le nocche, che sono l'unica cosa
 * che distingue davvero un pugno da un rettangolo arrotondato.
 */
fun DrawScope.drawFistGlyph(
    center: Offset,
    palmWidth: Float,
    color: Color,
) {
    val palmHeight = palmWidth * 0.92f
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - palmWidth / 2f, center.y - palmHeight / 2f),
        size = Size(palmWidth, palmHeight),
        cornerRadius = CornerRadius(palmWidth * 0.32f),
    )

    val knuckle = palmWidth * 0.15f
    for (index in 0..3) {
        drawCircle(
            color = color.copy(alpha = 0.45f),
            radius = knuckle / 2f,
            center = Offset(
                x = center.x - palmWidth * 0.30f + index * palmWidth * 0.20f,
                y = center.y - palmHeight * 0.26f,
            ),
        )
    }

    // Il pollice piegato di traverso, come in un pugno vero.
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - palmWidth * 0.58f, center.y + palmHeight * 0.02f),
        size = Size(palmWidth * 0.34f, palmWidth * 0.20f),
        cornerRadius = CornerRadius(palmWidth * 0.10f),
    )
}

/**
 * Pollice in su: il pugno con il solo pollice disteso verso l'alto.
 *
 * E' il gesto di attivazione, quello che l'utente deve imparare per primo.
 */
fun DrawScope.drawThumbUpGlyph(
    center: Offset,
    palmWidth: Float,
    color: Color,
) {
    val palmHeight = palmWidth * 0.92f
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - palmWidth * 0.42f, center.y - palmHeight * 0.34f),
        size = Size(palmWidth * 0.84f, palmHeight * 0.86f),
        cornerRadius = CornerRadius(palmWidth * 0.28f),
    )

    val fingerWidth = palmWidth * 0.16f
    for (index in 0..2) {
        drawRoundRect(
            color = color.copy(alpha = 0.45f),
            topLeft = Offset(
                center.x - palmWidth * 0.30f + index * palmWidth * 0.22f,
                center.y - palmHeight * 0.22f,
            ),
            size = Size(fingerWidth, palmHeight * 0.16f),
            cornerRadius = CornerRadius(fingerWidth / 2f),
        )
    }

    // Il pollice, che esce dal lato e punta in su: e' cio' che rende il gesto
    // leggibile, quindi e' l'elemento piu' lungo del disegno.
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - palmWidth * 0.62f, center.y - palmHeight * 0.86f),
        size = Size(palmWidth * 0.26f, palmHeight * 0.72f),
        cornerRadius = CornerRadius(palmWidth * 0.13f),
    )
}
