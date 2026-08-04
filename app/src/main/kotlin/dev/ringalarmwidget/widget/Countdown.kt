package dev.ringalarmwidget.widget

import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.compose.ui.unit.dp
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import dev.ringalarmwidget.R

@Composable
internal fun Countdown(endsAt: Long, colorArgb: Int) {
    AndroidRemoteViews(remoteViews = countdownViews(R.layout.widget_countdown, endsAt, colorArgb))
}

@Composable
internal fun CountdownTile(endsAt: Long, colorArgb: Int) {
    AndroidRemoteViews(
        remoteViews = countdownViews(R.layout.widget_countdown_tile, endsAt, colorArgb),
        modifier = GlanceModifier.fillMaxWidth().height(18.dp),
    )
}

@Composable
private fun countdownViews(layout: Int, endsAt: Long, colorArgb: Int): RemoteViews {
    val context = LocalContext.current
    val base = SystemClock.elapsedRealtime() + (endsAt - System.currentTimeMillis())

    return RemoteViews(context.packageName, layout).apply {
        setChronometerCountDown(R.id.countdown, true)
        setChronometer(R.id.countdown, base, null, true)
        setTextColor(R.id.countdown, colorArgb)
    }
}
