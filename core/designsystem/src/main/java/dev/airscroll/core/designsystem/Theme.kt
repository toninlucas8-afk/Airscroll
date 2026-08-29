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
    error = StatusIdle,
)

private val LightScheme = lightColorScheme(
    primary = ForestDeep,
    onPrimary = Chalk,
    background = Paper,
    onBackground = Midnight,
    surface = PaperRaised,
    onSurface = Midnight,
    error = StatusIdle,
)

private val AirTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
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
