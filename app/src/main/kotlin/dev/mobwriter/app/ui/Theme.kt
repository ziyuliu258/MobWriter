package dev.mobwriter.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Paper = Color(0xFFF7F3EA)
val Ink = Color(0xFF1E1A16)
val Cinnabar = Color(0xFF8E3B32)
val Moss = Color(0xFF215C45)
val FormulaGround = Color(0xFFE5F0EA)
val Muted = Color(0xFF8A8175)

private val LightColors = lightColorScheme(
    primary = Cinnabar,
    onPrimary = Color(0xFFFFF8F2),
    secondary = Moss,
    background = Paper,
    surface = Color(0xFFFFFBF5),
    surfaceVariant = Color(0xFFE7E0D4),
    onBackground = Ink,
    onSurface = Ink,
    outline = Color(0xFFD3C8B8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE7B2A8),
    onPrimary = Color(0xFF3A1612),
    secondary = Color(0xFF9DCCB4),
    background = Color(0xFF16130F),
    surface = Color(0xFF221E19),
    surfaceVariant = Color(0xFF3A332B),
    onBackground = Color(0xFFF3EBDD),
    onSurface = Color(0xFFF3EBDD),
)

@Composable
fun MobWriterTheme(content: @Composable () -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = MaterialTheme.typography.copy(
            headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
            titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
            bodyLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 28.sp),
        ),
        content = content,
    )
}
