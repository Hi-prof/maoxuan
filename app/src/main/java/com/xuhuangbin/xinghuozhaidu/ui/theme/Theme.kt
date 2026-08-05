package com.xuhuangbin.xinghuozhaidu.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.xuhuangbin.xinghuozhaidu.R

val Ink = Color(0xFF272522)
val MutedInk = Color(0xFF6E6962)
val SpiritRed = Color(0xFFA52A2E)
val DeepRed = Color(0xFF7E2024)
val Paper = Color(0xFFF8F6F0)
val Canvas = Color(0xFFF0F0ED)
val Divider = Color(0xFFD9D7D1)
val SoftRed = Color(0xFFF1E4E3)
val ArchiveGreen = Color(0xFF58665C)

val QuoteFontFamily = FontFamily(
    Font(R.font.noto_serif_sc, weight = FontWeight.Normal),
    Font(R.font.noto_serif_sc, weight = FontWeight.Bold),
)

private val XinghuoColors = lightColorScheme(
    primary = SpiritRed,
    onPrimary = Color.White,
    primaryContainer = SoftRed,
    onPrimaryContainer = DeepRed,
    secondary = DeepRed,
    onSecondary = Color.White,
    secondaryContainer = SoftRed,
    onSecondaryContainer = Ink,
    tertiary = MutedInk,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE7E5DF),
    onTertiaryContainer = Ink,
    background = Canvas,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE8E7E2),
    onSurfaceVariant = MutedInk,
    outline = Divider,
    error = Color(0xFFB3261E),
)

@Composable
fun XinghuoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = XinghuoColors,
        content = content,
    )
}
