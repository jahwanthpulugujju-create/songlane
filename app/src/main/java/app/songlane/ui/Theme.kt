package app.songlane.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF111510)
val Panel = Color(0xFF1E241C)
val Sage = Color(0xFFCAEF91)
val Muted = Color(0xFFA9B2A2)
val Cream = Color(0xFFF0F2E8)
val Coral = Color(0xFFE6A389)
private val colors =
    darkColorScheme(
        primary = Sage,
        onPrimary = Ink,
        secondary = Coral,
        onSecondary = Ink,
        background = Ink,
        onBackground = Cream,
        surface = Ink,
        onSurface = Cream,
        surfaceVariant = Panel,
        onSurfaceVariant = Muted,
        primaryContainer = Color(0xFF33412C),
        onPrimaryContainer = Sage,
        secondaryContainer = Color(0xFF35402E),
        onSecondaryContainer = Cream,
        surfaceContainer = Panel,
        surfaceContainerLow = Panel,
        surfaceContainerHigh = Color(0xFF293125),
        surfaceContainerHighest = Color(0xFF323C2D),
        outline = Color(0xFF424B3D),
        error = Color(0xFFFFB4AB),
    )
private val type =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = 48.sp,
                lineHeight = 51.sp,
                letterSpacing = (-1.8).sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = 36.sp,
                lineHeight = 40.sp,
                letterSpacing = (-1).sp,
            ),
        headlineMedium =
            TextStyle(fontFamily = FontFamily.Serif, fontSize = 28.sp, lineHeight = 33.sp),
        titleLarge =
            TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.Medium),
        titleMedium =
            TextStyle(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
        labelSmall =
            TextStyle(
                fontSize = 10.sp,
                lineHeight = 15.sp,
                letterSpacing = 1.4.sp,
                fontWeight = FontWeight.Medium,
            ),
    )

@Composable
fun SonglaneTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = type, content = content)
}
