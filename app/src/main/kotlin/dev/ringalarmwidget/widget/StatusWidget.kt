package dev.ringalarmwidget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import dev.ringalarmwidget.R
import dev.ringalarmwidget.core.panel.AlarmMode
import dev.ringalarmwidget.data.AppContainer
import dev.ringalarmwidget.data.WidgetSnapshot
import dev.ringalarmwidget.ui.MainActivity

class StatusWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshots = AppContainer.get(context).store.widgetSnapshots()

        provideContent {
            val snapshot by snapshots.collectAsState(WidgetSnapshot.Empty)

            Tile(
                skin = skinFor(context, snapshot.theme),
                shown = if (snapshot.signedIn) snapshot.pending ?: snapshot.mode else null,
                pending = snapshot.pending != null,
                transitionEndsAt = snapshot.transitionEndsAt,
            )
        }
    }
}

@Composable
private fun Tile(skin: Skin, shown: AlarmMode?, pending: Boolean, transitionEndsAt: Long?) {
    val context = LocalContext.current
    val known = shown != null

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(shown?.let { pillOf(skin, it, true) } ?: skin.pill))
            .padding(6.dp)
            .clickable(actionStartActivity<MainActivity>()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val counting = transitionEndsAt != null

        Image(
            provider = ImageProvider(R.drawable.ic_logo),
            contentDescription = null,
            colorFilter = ColorFilter.tint(if (known) OnAccent else skin.muted),
            modifier = GlanceModifier.size(if (counting) 20.dp else 28.dp),
        )

        Spacer(GlanceModifier.height(if (counting) 3.dp else 6.dp))

        Text(
            text = context.getString(
                when {
                    pending && !counting -> R.string.widget_state_pending
                    shown != null -> labelOf(shown)
                    else -> R.string.widget_state_unknown
                }
            ),
            style = TextStyle(
                color = if (known) OnAccent else skin.ink,
                fontSize = if (counting) 11.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            maxLines = if (counting) 1 else 2,
        )

        if (transitionEndsAt != null) {
            CountdownTile(
                endsAt = transitionEndsAt,
                colorArgb = if (known) OnAccentArgb else skin.mutedArgb,
            )
        }
    }
}
