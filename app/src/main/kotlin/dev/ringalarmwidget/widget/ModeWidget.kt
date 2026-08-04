package dev.ringalarmwidget.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.ringalarmwidget.R
import dev.ringalarmwidget.core.panel.AlarmMode
import dev.ringalarmwidget.data.AppContainer
import dev.ringalarmwidget.data.WidgetSnapshot
import dev.ringalarmwidget.ui.MainActivity

private val CompactSize = DpSize(140.dp, 40.dp)
private val TallSize = DpSize(140.dp, 120.dp)

class ModeWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(CompactSize, TallSize))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshots = AppContainer.get(context).store.widgetSnapshots()

        provideContent {
            val snapshot by snapshots.collectAsState(WidgetSnapshot.Empty)

            Body(
                skin = skinFor(context, snapshot.theme),
                signedIn = snapshot.signedIn,
                mode = snapshot.mode,
                pending = snapshot.pending,
                failed = snapshot.failed,
                transitionEndsAt = snapshot.transitionEndsAt,
            )
        }
    }
}

@Composable
private fun Body(
    skin: Skin,
    signedIn: Boolean,
    mode: AlarmMode?,
    pending: AlarmMode?,
    failed: Boolean,
    transitionEndsAt: Long?,
) {
    val compact = LocalSize.current.height < TallSize.height
    val shown = pending ?: mode

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(skin.background))
            .padding(if (compact) 8.dp else 14.dp),
    ) {
        if (!compact) {
            Header(
                skin = skin,
                shown = shown,
                pending = pending != null,
                failed = failed,
                transitionEndsAt = transitionEndsAt,
            )

            if (transitionEndsAt != null) {
                Spacer(GlanceModifier.height(5.dp))
                LinearProgressIndicator(
                    color = accentOf(skin, shown),
                    modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                )
                Spacer(GlanceModifier.height(5.dp))
            } else {
                Spacer(GlanceModifier.height(10.dp))
            }
        }

        if (signedIn) {
            Modes(
                skin = skin,
                shown = shown,
                locked = pending != null,
                note = if (compact) noteOf(pending != null, failed) else null,
                countdown = if (compact) transitionEndsAt else null,
                compact = compact,
            )
        } else {
            SignInHint(skin = skin, compact = compact)
        }
    }
}

@Composable
private fun Header(
    skin: Skin,
    shown: AlarmMode?,
    pending: Boolean,
    failed: Boolean,
    transitionEndsAt: Long?,
) {
    val context = LocalContext.current

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>())) {
            Text(
                text = context.getString(shown?.let(::labelOf) ?: R.string.widget_state_unknown),
                style = TextStyle(color = accentOf(skin, shown), fontSize = 18.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            if (transitionEndsAt != null) {
                Countdown(endsAt = transitionEndsAt, colorArgb = skin.mutedArgb)
            } else if (pending || failed) {
                Text(
                    text = context.getString(if (pending) R.string.widget_state_pending else R.string.widget_failed),
                    style = TextStyle(color = skin.muted, fontSize = 11.sp),
                    maxLines = 1,
                )
            }
        }

        Image(
            provider = ImageProvider(R.drawable.ic_refresh),
            contentDescription = context.getString(R.string.widget_refresh),
            colorFilter = ColorFilter.tint(skin.muted),
            modifier = GlanceModifier
                .size(22.dp)
                .clickable(actionRunCallback<RefreshAction>()),
        )
    }
}

@Composable
private fun Modes(
    skin: Skin,
    shown: AlarmMode?,
    locked: Boolean,
    note: Int?,
    countdown: Long?,
    compact: Boolean,
) {
    Row(modifier = GlanceModifier.fillMaxWidth().fillMaxHeight()) {
        AlarmMode.entries.forEachIndexed { index, mode ->
            if (index > 0) Spacer(GlanceModifier.width(if (compact) 6.dp else 8.dp))
            ModeCell(
                skin = skin,
                mode = mode,
                active = shown == mode,
                locked = locked,
                note = note,
                countdown = countdown,
                compact = compact,
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun ModeCell(
    skin: Skin,
    mode: AlarmMode,
    active: Boolean,
    locked: Boolean,
    note: Int?,
    countdown: Long?,
    compact: Boolean,
    modifier: GlanceModifier,
) {
    val context = LocalContext.current
    val filled = modifier.background(ImageProvider(pillOf(skin, mode, active)))
    val cell = when {
        locked -> filled

        !active -> filled.clickable(
            actionRunCallback<SetModeAction>(actionParametersOf(ModeKey to mode.name))
        )

        compact -> filled.clickable(actionRunCallback<RefreshAction>())

        else -> filled
    }

    Box(modifier = cell, contentAlignment = Alignment.Center) {
        if (active && countdown != null) {
            Countdown(endsAt = countdown, colorArgb = OnAccentArgb)
            return@Box
        }

        Text(
            text = context.getString(if (active && note != null) note else labelOf(mode)),
            style = TextStyle(
                color = if (active) OnAccent else skin.ink,
                fontSize = if (compact) 11.sp else 12.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            maxLines = 2,
        )
    }
}

@Composable
private fun SignInHint(skin: Skin, compact: Boolean) {
    val context = LocalContext.current

    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(ImageProvider(skin.pill))
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = context.getString(R.string.widget_sign_in),
            style = TextStyle(
                color = skin.ink,
                fontSize = if (compact) 12.sp else 13.sp,
                textAlign = TextAlign.Center,
            ),
            maxLines = 2,
        )
    }
}

private fun noteOf(pending: Boolean, failed: Boolean): Int? = when {
    pending -> R.string.widget_state_pending
    failed -> R.string.widget_failed
    else -> null
}
