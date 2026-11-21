package com.lifeorganizer.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Monochrome palette. */
object Mono {
    val Black = Color(0xFF000000)
    val Ink = Color(0xFF111111)
    val G800 = Color(0xFF333333)
    val G600 = Color(0xFF666666)
    val G500 = Color(0xFF8A8A8A)
    val G400 = Color(0xFFAAAAAA)
    val G300 = Color(0xFFCFCFCF)
    val G200 = Color(0xFFE4E4E4)
    val G100 = Color(0xFFF1F1F1)
    val G50 = Color(0xFFF8F8F8)
    val White = Color(0xFFFFFFFF)
}

private val colors = lightColorScheme(
    primary = Mono.Black,
    onPrimary = Mono.White,
    primaryContainer = Mono.G100,
    onPrimaryContainer = Mono.Black,
    inversePrimary = Mono.G300,
    secondary = Mono.G800,
    onSecondary = Mono.White,
    secondaryContainer = Mono.G200,
    onSecondaryContainer = Mono.Black,
    tertiary = Mono.G600,
    onTertiary = Mono.White,
    tertiaryContainer = Mono.G100,
    onTertiaryContainer = Mono.Black,
    background = Mono.White,
    onBackground = Mono.Ink,
    surface = Mono.White,
    onSurface = Mono.Ink,
    surfaceVariant = Mono.G100,
    onSurfaceVariant = Mono.G600,
    surfaceTint = Mono.White,
    inverseSurface = Mono.Ink,
    inverseOnSurface = Mono.White,
    error = Mono.Black,
    onError = Mono.White,
    errorContainer = Mono.G200,
    onErrorContainer = Mono.Black,
    outline = Mono.G300,
    outlineVariant = Mono.G200,
    scrim = Mono.Black,
    surfaceBright = Mono.White,
    surfaceDim = Mono.G100,
    surfaceContainer = Mono.White,
    surfaceContainerHigh = Mono.White,
    surfaceContainerHighest = Mono.G100,
    surfaceContainerLow = Mono.White,
    surfaceContainerLowest = Mono.White,
)

private val base = Typography()
private val typography = Typography(
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium,
    bodySmall = base.bodySmall.copy(color = Mono.G600),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
    labelMedium = base.labelMedium,
    labelSmall = base.labelSmall.copy(letterSpacing = 0.6.sp),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/** Small caps-like label used for section headers. */
val SectionLabel = TextStyle(fontFamily = FontFamily.Default, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, color = Mono.G500)

@Composable
fun OrganizerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
}
