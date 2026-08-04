package dev.ringalarmwidget.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.glance.unit.ColorProvider
import dev.ringalarmwidget.R
import dev.ringalarmwidget.core.panel.AlarmMode
import dev.ringalarmwidget.data.ThemeChoice
import dev.ringalarmwidget.ui.theme.Amber
import dev.ringalarmwidget.ui.theme.AmberDark
import dev.ringalarmwidget.ui.theme.InkDark
import dev.ringalarmwidget.ui.theme.InkLight
import dev.ringalarmwidget.ui.theme.MutedDark
import dev.ringalarmwidget.ui.theme.MutedLight
import dev.ringalarmwidget.ui.theme.Slate
import dev.ringalarmwidget.ui.theme.SlateDark
import dev.ringalarmwidget.ui.theme.Violet
import dev.ringalarmwidget.ui.theme.VioletDark

internal class Skin(
    val background: Int,
    val pill: Int,
    val ink: ColorProvider,
    val muted: ColorProvider,
    val mutedArgb: Int,
    val disarmed: ColorProvider,
    val home: ColorProvider,
    val away: ColorProvider,
)

private val LightSkin = Skin(
    background = R.drawable.widget_bg_light,
    pill = R.drawable.widget_pill_light,
    ink = ColorProvider(InkLight),
    muted = ColorProvider(MutedLight),
    mutedArgb = MutedLight.toArgb(),
    disarmed = ColorProvider(Slate),
    home = ColorProvider(Amber),
    away = ColorProvider(Violet),
)

private val DarkSkin = Skin(
    background = R.drawable.widget_bg_dark,
    pill = R.drawable.widget_pill_dark,
    ink = ColorProvider(InkDark),
    muted = ColorProvider(MutedDark),
    mutedArgb = MutedDark.toArgb(),
    disarmed = ColorProvider(SlateDark),
    home = ColorProvider(AmberDark),
    away = ColorProvider(VioletDark),
)

internal val OnAccent = ColorProvider(Color.White)
internal val OnAccentArgb = Color.White.toArgb()

internal fun skinFor(context: Context, choice: ThemeChoice): Skin =
    if (isDark(context, choice)) DarkSkin else LightSkin

private fun isDark(context: Context, choice: ThemeChoice): Boolean = when (choice) {
    ThemeChoice.LIGHT -> false
    ThemeChoice.DARK -> true
    ThemeChoice.SYSTEM ->
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
}

internal fun accentOf(skin: Skin, mode: AlarmMode?): ColorProvider = when (mode) {
    AlarmMode.DISARMED -> skin.disarmed
    AlarmMode.HOME -> skin.home
    AlarmMode.AWAY -> skin.away
    null -> skin.muted
}

internal fun pillOf(skin: Skin, mode: AlarmMode, active: Boolean): Int = when {
    !active -> skin.pill
    mode == AlarmMode.DISARMED -> R.drawable.widget_pill_disarmed
    mode == AlarmMode.HOME -> R.drawable.widget_pill_home
    else -> R.drawable.widget_pill_away
}

internal fun labelOf(mode: AlarmMode): Int = when (mode) {
    AlarmMode.DISARMED -> R.string.mode_disarmed
    AlarmMode.HOME -> R.string.mode_home
    AlarmMode.AWAY -> R.string.mode_away
}
