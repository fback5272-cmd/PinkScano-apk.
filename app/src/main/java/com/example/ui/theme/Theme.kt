package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val PinkScanLightColorScheme = lightColorScheme(
    primary = PinkPrimary,
    onPrimary = PureWhite,
    primaryContainer = PinkLight,
    onPrimaryContainer = PinkDeep,
    secondary = PinkDeep,
    onSecondary = PureWhite,
    secondaryContainer = PinkSurfaceVariantLight,
    onSecondaryContainer = DarkText,
    tertiary = PinkPrimary,
    onTertiary = PureWhite,
    background = PinkSoftBackground,
    onBackground = DarkText,
    surface = PureWhite,
    onSurface = DarkText,
    surfaceVariant = PinkSurfaceVariantLight,
    onSurfaceVariant = SecondaryText,
    surfaceContainerLow = PureWhite,
    surfaceContainer = PinkSurfaceVariantLight,
    surfaceContainerHigh = PinkContainerHighLight,
    outline = PinkBorderLight,
    outlineVariant = PinkBorderLight,
    error = ErrorRed,
    onError = PureWhite
)

private val PinkScanDarkColorScheme = darkColorScheme(
    primary = PinkPrimaryDark,
    onPrimary = PinkDarkBackground,
    primaryContainer = PinkDarkSurfaceVariant,
    onPrimaryContainer = PinkLight,
    secondary = PinkDeepDark,
    onSecondary = PureWhite,
    secondaryContainer = PinkDarkSurfaceVariant,
    onSecondaryContainer = PinkDarkText,
    tertiary = PinkPrimaryDark,
    onTertiary = PinkDarkBackground,
    background = PinkDarkBackground,
    onBackground = PinkDarkText,
    surface = PinkDarkSurface,
    onSurface = PinkDarkText,
    surfaceVariant = PinkDarkSurfaceVariant,
    onSurfaceVariant = PinkDarkSecondaryText,
    surfaceContainerLow = PinkDarkSurface,
    surfaceContainer = PinkDarkSurfaceVariant,
    surfaceContainerHigh = PinkDarkBorder,
    outline = PinkDarkBorder,
    outlineVariant = PinkDarkBorder,
    error = ErrorRed,
    onError = PureWhite
)

val PinkScanShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun PinkScanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) PinkScanDarkColorScheme else PinkScanLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = PinkScanTypography,
        shapes = PinkScanShapes,
        content = content
    )
}
