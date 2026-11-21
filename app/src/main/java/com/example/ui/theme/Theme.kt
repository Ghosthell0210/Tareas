package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = CoralPulse,
    onPrimary = Color.White,
    primaryContainer = CoralPulseSoft,
    onPrimaryContainer = Color(0xFF5D1305),
    secondary = MidnightSlate,
    onSecondary = Color.White,
    secondaryContainer = SoftCoolVariant,
    onSecondaryContainer = MidnightSlate,
    tertiary = ElectricMint,
    onTertiary = Color.White,
    tertiaryContainer = ElectricMintLight,
    onTertiaryContainer = Color(0xFF064E3B),
    background = AlabasterBg,
    onBackground = InkDark,
    surface = PureWhiteSurface,
    onSurface = InkDark,
    surfaceVariant = SoftCoolVariant,
    onSurfaceVariant = MutedSlateText,
    outline = SubtleOutlineLight,
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

private val DarkColorScheme = darkColorScheme(
    primary = CoralPulseDark,
    onPrimary = Color(0xFF280600),
    primaryContainer = CoralContainerDark,
    onPrimaryContainer = Color(0xFFFFDAD2),
    secondary = Color(0xFF9CB0D8),
    onSecondary = MidnightSlate,
    secondaryContainer = SurfaceSlateHigh,
    onSecondaryContainer = CrispOffWhite,
    tertiary = ElectricMintDark,
    onTertiary = Color(0xFF002116),
    tertiaryContainer = MintContainerDark,
    onTertiaryContainer = Color(0xFFA7F3D0),
    background = DeepObsidian,
    onBackground = CrispOffWhite,
    surface = ElevatedSlate,
    onSurface = CrispOffWhite,
    surfaceVariant = SurfaceSlateHigh,
    onSurfaceVariant = MutedCoolTextDark,
    outline = SubtleOutlineDark,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA)
)

val PulsoShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PulsoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PulsoTypography,
        shapes = PulsoShapes,
        content = content
    )
}
