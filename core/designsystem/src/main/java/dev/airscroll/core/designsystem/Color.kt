package dev.airscroll.core.designsystem

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Palette "Mezzanotte & Menta": fondo scuro profondo, un solo accento verde.
// Uno schermo scuro consuma meno su OLED e non acceca in cucina di sera.
val Midnight = Color(0xFF060F0B)
val MidnightRaised = Color(0xFF0B1712)
val MidnightCard = Color(0xFF0F2018)
val MidnightBorder = Color(0xFF1C3A2B)

val Mint = Color(0xFF2DE39A)
val MintSoft = Color(0xFF4ADE80)
val MintDim = Color(0xFF1B7A55)

val Chalk = Color(0xFFF1F5F2)
val ChalkMuted = Color(0xFF93A99D)

val StatusIdle = Color(0xFFE0483B)
val StatusWaiting = Color(0xFFF2B33D)
val StatusActive = Color(0xFF2DE39A)

// Variante chiara, per chi tiene il telefono in modalita' luminosa.
val ForestDeep = Color(0xFF1F7A55)
val Paper = Color(0xFFF6FAF7)
val PaperRaised = Color(0xFFFFFFFF)
val PaperBorder = Color(0xFFD8E6DE)
val SlateMuted = Color(0xFF55705F)

/** Gradiente del logotipo: da menta a verde foglia, in diagonale. */
val WordmarkBrush: Brush
    get() = Brush.linearGradient(listOf(Mint, MintSoft))

/** Alone sotto la card di stato, per staccarla dal fondo senza usare ombre. */
fun glowBrush(color: Color): Brush = Brush.radialGradient(
    colors = listOf(color.copy(alpha = 0.28f), Color.Transparent),
)
