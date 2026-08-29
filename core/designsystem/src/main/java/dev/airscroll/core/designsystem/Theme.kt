package dev.airscroll.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkScheme = darkColorScheme(
    primary = Mint,
    onPrimary = Midnight,
    primaryContainer = MidnightCard,
    onPrimaryContainer = Mint,
    secondary = MintSoft,
    onSecondary = Midnight,
    background = Midnight,
    onBackground = Chalk,
    surface = MidnightRaised,
    onSurface = Chalk,
    surfaceVariant = MidnightCard,
    onSurfaceVariant = ChalkMuted,
    outline = MidnightBorder,
    outlineVariant = MidnightBorder,
    error = StatusIdle,
    onError = Chalk,
)

private val LightScheme = lightColorScheme(
    primary = ForestDeep,
    onPrimary = PaperRaised,
    primaryContainer = Paper,
    onPrimaryContainer = ForestDeep,
    background = Paper,
    onBackground = Midnight,
    surface = PaperRaised,
    onSurface = Midnight,
    surfaceVariant = PaperRaised,
    onSurfaceVariant = SlateMuted,
    outline = PaperBorder,
    outlineVariant = PaperBorder,
    error = StatusIdle,
)

private val AirTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, lineHeight = 34.sp, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontSize = 23.sp, fontWeight = FontWeight.SemiBold, lineHeight = 29.sp),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, lineHeight = 25.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.5.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
)

@Composable
fun AirScrollTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = AirTypography,
        content = content,
    )
}
