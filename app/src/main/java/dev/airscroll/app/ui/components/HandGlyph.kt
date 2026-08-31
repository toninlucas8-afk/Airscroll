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
 * Pollice in su: un pugno con il pollice disteso verso l'alto.
 *
 * E' il gesto di attivazione, il primo che l'utente deve imparare, e per questo
 * e' l'unico che non puo' permettersi di essere ambiguo.
 *
 * La prima versione non si leggeva: il pollice era una barretta staccata
 * accanto a un rettangolo arrotondato, e alla prova su telefono e' arrivato il
 * verdetto giusto - "non sembra per niente un pollice". Ricostruita dalla
 * sagoma invece che dai dettagli, perche' a 64 px la sagoma e' l'unica cosa
 * che si vede:
 *
 * - il **polso** sotto il pugno: senza, un pugno resta un blocco qualunque;
 * - il **pugno** leggermente piu' largo che alto;
 * - il **pollice** che esce dal lato e si sovrappone al pugno, cosi' e'
 *   attaccato invece che appoggiato accanto;
 * - due **tacche** delle dita piegate, disegnate nel colore dello sfondo:
 *   sopra un pieno dello stesso colore, un solco e' l'unico modo per farle
 *   vedere.
 *
 * @param gap colore dello sfondo su cui il disegno viene posato, per le tacche.
 */
fun DrawScope.drawThumbUpGlyph(
    center: Offset,
    palmWidth: Float,
    color: Color,
    gap: Color,
) {
    val fistWidth = palmWidth * 0.84f
    val fistHeight = palmWidth * 0.72f
    val fistX = center.x - palmWidth * 0.30f
    val fistY = center.y - palmWidth * 0.16f

    // Il polso va disegnato per primo: gli sta dietro.
    drawRoundRect(
        color = color,
        topLeft = Offset(fistX + fistWidth * 0.14f, fistY + fistHeight * 0.78f),
        size = Size(fistWidth * 0.62f, palmWidth * 0.30f),
        cornerRadius = CornerRadius(palmWidth * 0.10f),
    )

    drawRoundRect(
        color = color,
        topLeft = Offset(fistX, fistY),
        size = Size(fistWidth, fistHeight),
        cornerRadius = CornerRadius(palmWidth * 0.24f),
    )

    val thumbWidth = palmWidth * 0.30f
    val thumbHeight = palmWidth * 0.66f
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - palmWidth * 0.42f, fistY - thumbHeight + palmWidth * 0.18f),
        size = Size(thumbWidth, thumbHeight),
        cornerRadius = CornerRadius(thumbWidth / 2f),
    )

    for (index in 0..1) {
        drawRoundRect(
            color = gap,
            topLeft = Offset(
                fistX + fistWidth * 0.40f,
                fistY + fistHeight * (0.30f + 0.30f * index),
            ),
            size = Size(fistWidth * 0.48f, palmWidth * 0.055f),
            cornerRadius = CornerRadius(palmWidth * 0.03f),
        )
    }
}
