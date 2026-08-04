package dev.ringalarmwidget.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ringalarmwidget.R
import dev.ringalarmwidget.core.panel.AlarmMode

val Violet = Color(0xFF7048E8)
val VioletDark = Color(0xFF9775FA)
val Amber = Color(0xFFF08C00)
val AmberDark = Color(0xFFFFC078)
val Slate = Color(0xFF868E96)
val SlateDark = Color(0xFFADB5BD)
val Danger = Color(0xFFE5484D)

val InkLight = Color(0xFF16151A)
val InkDark = Color(0xFFF2F0F5)
val MutedLight = Color(0xFF6C6A75)
val MutedDark = Color(0xFFA5A2B0)

private val CanvasLight = Color(0xFFF8F7FB)
private val CanvasDark = Color(0xFF121116)
private val SurfaceLight = Color(0xFFFFFFFF)
private val SurfaceDark = Color(0xFF1D1B22)

val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

data class ModePalette(
    val disarmed: Color,
    val home: Color,
    val away: Color,
) {
    fun of(mode: AlarmMode): Color = when (mode) {
        AlarmMode.DISARMED -> disarmed
        AlarmMode.HOME -> home
        AlarmMode.AWAY -> away
    }
}

val LocalModePalette = staticCompositionLocalOf {
    ModePalette(disarmed = Slate, home = Amber, away = Violet)
}

private val LightScheme = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    background = CanvasLight,
    onBackground = InkLight,
    surface = SurfaceLight,
    onSurface = InkLight,
    surfaceVariant = Color(0xFFEDEAF4),
    onSurfaceVariant = MutedLight,
    outline = Color(0xFFD8D4E2),
    error = Danger,
)

private val DarkScheme = darkColorScheme(
    primary = VioletDark,
    onPrimary = Color(0xFF1A1030),
    background = CanvasDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = Color(0xFF2A2733),
    onSurfaceVariant = MutedDark,
    outline = Color(0xFF3B3844),
    error = Color(0xFFFF6369),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

private fun poppinsTypography(): Typography {
    val base = Typography()
    return Typography(
        displaySmall = base.displaySmall.poppins(FontWeight.Bold, 34.sp),
        headlineMedium = base.headlineMedium.poppins(FontWeight.Bold, 27.sp),
        headlineSmall = base.headlineSmall.poppins(FontWeight.SemiBold, 22.sp),
        titleMedium = base.titleMedium.poppins(FontWeight.SemiBold, 17.sp),
        titleSmall = base.titleSmall.poppins(FontWeight.Medium, 15.sp),
        bodyLarge = base.bodyLarge.poppins(FontWeight.Normal, 16.sp),
        bodyMedium = base.bodyMedium.poppins(FontWeight.Normal, 14.sp),
        bodySmall = base.bodySmall.poppins(FontWeight.Normal, 13.sp),
        labelLarge = base.labelLarge.poppins(FontWeight.SemiBold, 15.sp),
        labelMedium = base.labelMedium.poppins(FontWeight.Medium, 13.sp),
        labelSmall = base.labelSmall.poppins(FontWeight.Medium, 12.sp),
    )
}

private fun TextStyle.poppins(weight: FontWeight, size: androidx.compose.ui.unit.TextUnit) =
    copy(fontFamily = Poppins, fontWeight = weight, fontSize = size)

@Composable
fun RingAlarmTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (dark) {
        ModePalette(disarmed = SlateDark, home = AmberDark, away = VioletDark)
    } else {
        ModePalette(disarmed = Slate, home = Amber, away = Violet)
    }

    CompositionLocalProvider(LocalModePalette provides palette) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = poppinsTypography(),
            shapes = AppShapes,
            content = content,
        )
    }
}
