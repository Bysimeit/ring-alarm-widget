package dev.ringalarmwidget.data

import dev.ringalarmwidget.core.panel.AlarmMode
import dev.ringalarmwidget.core.panel.FaultedSensor
import kotlinx.serialization.Serializable

data class PanelCache(
    val locationId: String,
    val locationName: String?,
    val mode: AlarmMode?,
)

@Serializable
data class BypassPrompt(
    val mode: String,
    val requestedAtEpochMillis: Long,
    val sensorZids: List<String>,
    val sensorNames: List<String>,
) {
    val requested: AlarmMode? get() = AlarmMode.fromWire(mode)

    companion object {
        fun of(mode: AlarmMode, sensors: List<FaultedSensor>, nowEpochMillis: Long) = BypassPrompt(
            mode = mode.wireValue,
            requestedAtEpochMillis = nowEpochMillis,
            sensorZids = sensors.map { it.zid },
            sensorNames = sensors.mapNotNull { it.name },
        )
    }
}

data class WidgetSnapshot(
    val signedIn: Boolean,
    val mode: AlarmMode?,
    val pending: AlarmMode?,
    val failed: Boolean,
    val theme: ThemeChoice,
    val transitionEndsAt: Long?,
    val bypass: BypassPrompt?,
) {
    companion object {
        val Empty = WidgetSnapshot(
            signedIn = false,
            mode = null,
            pending = null,
            failed = false,
            theme = ThemeChoice.SYSTEM,
            transitionEndsAt = null,
            bypass = null,
        )
    }
}
