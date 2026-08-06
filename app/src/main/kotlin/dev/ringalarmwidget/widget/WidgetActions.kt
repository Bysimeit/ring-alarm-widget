package dev.ringalarmwidget.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import dev.ringalarmwidget.core.panel.AlarmMode
import dev.ringalarmwidget.data.AppContainer
import dev.ringalarmwidget.data.PanelRepository

val ModeKey = ActionParameters.Key<String>("mode")

internal suspend fun updateWidgets(context: Context) {
    ModeWidget().updateAll(context)
    StatusWidget().updateAll(context)
}

internal fun anyWidgetPlaced(context: Context): Boolean {
    val manager = AppWidgetManager.getInstance(context)
    return listOf(ModeWidgetReceiver::class.java, StatusWidgetReceiver::class.java).any {
        manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty()
    }
}

class ModeWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = ModeWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetWork.start(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetWork.start(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        if (!anyWidgetPlaced(context)) WidgetWork.stop(context)
    }
}

class StatusWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = StatusWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetWork.start(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetWork.start(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        if (!anyWidgetPlaced(context)) WidgetWork.stop(context)
    }
}

class SetModeAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val name = parameters[ModeKey] ?: return
        val mode = AlarmMode.entries.firstOrNull { it.name == name } ?: return

        AppContainer.get(context).store.setPendingMode(mode)
        updateWidgets(context)
        WidgetWork.apply(context, mode)
    }
}

class RefreshAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        WidgetWork.refresh(context)
    }
}

class ConfirmBypassAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val store = AppContainer.get(context).store
        val prompt = store.bypassPrompt() ?: return
        val mode = prompt.requested ?: return

        store.setBypassPrompt(null)
        store.setPendingMode(mode)
        updateWidgets(context)
        WidgetWork.apply(context, mode, prompt.sensorZids)
    }
}

class DismissBypassAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        PanelRepository(context).dismissBypass()
        updateWidgets(context)
    }
}
