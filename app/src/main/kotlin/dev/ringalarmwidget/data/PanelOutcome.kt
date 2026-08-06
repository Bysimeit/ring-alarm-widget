package dev.ringalarmwidget.data

import dev.ringalarmwidget.core.auth.LeaseFailure
import dev.ringalarmwidget.core.panel.AlarmMode
import dev.ringalarmwidget.core.panel.FaultedSensor
import dev.ringalarmwidget.core.panel.PanelResult
import dev.ringalarmwidget.core.panel.PanelSnapshot
import dev.ringalarmwidget.core.panel.isTransient

sealed interface PanelOutcome {
    data class Ready(val locationName: String?, val snapshot: PanelSnapshot) : PanelOutcome
    data object SignedOut : PanelOutcome
    data object NoLocation : PanelOutcome
    data class Unavailable(val cause: LeaseFailure) : PanelOutcome
    data class Failed(val result: PanelResult) : PanelOutcome

    data class NeedsBypass(
        val requested: AlarmMode,
        val sensors: List<FaultedSensor>,
    ) : PanelOutcome

    val isTransient: Boolean
        get() = when (this) {
            is Ready -> false
            SignedOut -> false
            NoLocation -> false
            is NeedsBypass -> false
            is Unavailable -> true
            is Failed -> result.isTransient
        }
}
