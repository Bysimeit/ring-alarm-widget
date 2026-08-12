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
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.unit.ColorProvider
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
import dev.ringalarmwidget.data.BypassPrompt
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
                refreshing = snapshot.refreshing,
                failed = snapshot.failed,
                stale = snapshot.stale,
                transitionEndsAt = snapshot.transitionEndsAt,
                bypass = snapshot.bypass,
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
    refreshing: Boolean,
    failed: Boolean,
    stale: Boolean,
    transitionEndsAt: Long?,
    bypass: BypassPrompt?,
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
            if (bypass != null) {
                BypassHeader(skin = skin, prompt = bypass)
            } else {
                Header(
                    skin = skin,
                    shown = shown,
                    note = headerNoteOf(pending != null, refreshing, failed, stale),
                    transitionEndsAt = transitionEndsAt,
                )
            }

            if (transitionEndsAt != null && bypass == null) {
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

        when {
            !signedIn -> SignInHint(skin = skin, compact = compact)

            bypass != null -> BypassChoice(skin = skin, prompt = bypass, compact = compact)

            else -> Modes(
                skin = skin,
                shown = shown,
                locked = pending != null,
                note = if (compact) noteOf(pending != null, refreshing, failed) else null,
                countdown = if (compact) transitionEndsAt else null,
                compact = compact,
            )
        }
    }
}

@Composable
private fun BypassHeader(skin: Skin, prompt: BypassPrompt) {
    val context = LocalContext.current

    Column {
        Text(
            text = context.getString(R.string.widget_bypass_question),
            style = TextStyle(
                color = accentOf(skin, prompt.requested),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        Text(
            text = sensorSummary(context, prompt),
            style = TextStyle(color = skin.muted, fontSize = 11.sp),
            maxLines = 1,
        )
    }
}

@Composable
private fun BypassChoice(skin: Skin, prompt: BypassPrompt, compact: Boolean) {
    val context = LocalContext.current

    Row(modifier = GlanceModifier.fillMaxWidth().fillMaxHeight()) {
        if (compact) {
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight()
                    .background(ImageProvider(skin.pill)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = context.getString(R.string.widget_bypass_question),
                    style = TextStyle(color = skin.ink, fontSize = 11.sp, textAlign = TextAlign.Center),
                    maxLines = 2,
                )
            }
            Spacer(GlanceModifier.width(6.dp))
        }

        BypassButton(
            label = context.getString(R.string.widget_bypass_accept),
            description = context.getString(R.string.widget_bypass_accept_description),
            background = prompt.requested?.let { pillOf(skin, it, true) } ?: skin.pill,
            color = OnAccent,
            callback = actionRunCallback<ConfirmBypassAction>(),
            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
        )

        Spacer(GlanceModifier.width(if (compact) 6.dp else 8.dp))

        BypassButton(
            label = context.getString(R.string.widget_bypass_refuse),
            description = context.getString(R.string.widget_bypass_refuse_description),
            background = skin.pill,
            color = skin.ink,
            callback = actionRunCallback<DismissBypassAction>(),
            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
        )
    }
}

@Composable
private fun BypassButton(
    label: String,
    description: String,
    background: Int,
    color: ColorProvider,
    callback: Action,
    modifier: GlanceModifier,
) {
    Box(
        modifier = modifier
            .background(ImageProvider(background))
            .semantics { contentDescription = description }
            .clickable(callback),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = color,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}

private fun sensorSummary(context: Context, prompt: BypassPrompt): String {
    val names = prompt.sensorNames
    return when {
        names.isEmpty() -> context.getString(R.string.widget_bypass_sensors_unknown)
        else -> names.joinToString(", ")
    }
}

@Composable
private fun Header(
    skin: Skin,
    shown: AlarmMode?,
    note: Int?,
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
            } else if (note != null) {
                Text(
                    text = context.getString(note),
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

private fun noteOf(pending: Boolean, refreshing: Boolean, failed: Boolean): Int? = when {
    pending -> R.string.widget_state_pending
    refreshing -> R.string.widget_state_refreshing
    failed -> R.string.widget_failed
    else -> null
}

private fun headerNoteOf(pending: Boolean, refreshing: Boolean, failed: Boolean, stale: Boolean): Int? =
    noteOf(pending, refreshing, failed) ?: R.string.widget_state_stale.takeIf { stale }
