package dev.ringalarmwidget.data

import dev.ringalarmwidget.core.panel.AlarmMode

data class PanelCache(
    val locationId: String,
    val locationName: String?,
    val mode: AlarmMode?,
)

data class WidgetSnapshot(
    val signedIn: Boolean,
    val mode: AlarmMode?,
    val pending: AlarmMode?,
    val failed: Boolean,
    val theme: ThemeChoice,
    val transitionEndsAt: Long?,
) {
    companion object {
        val Empty = WidgetSnapshot(
            signedIn = false,
            mode = null,
            pending = null,
            failed = false,
            theme = ThemeChoice.SYSTEM,
            transitionEndsAt = null,
        )
    }
}
