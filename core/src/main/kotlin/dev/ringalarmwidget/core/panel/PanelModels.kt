package dev.ringalarmwidget.core.panel

data class RingLocation(
    val id: String,
    val name: String,
)

data class ConnectionAsset(
    val uuid: String,
    val kind: String,
    val status: String,
) {
    val carriesDevices: Boolean
        get() = kind.startsWith("base_station") || kind.startsWith("beams_bridge")
}

data class ConnectionTicket(
    val host: String,
    val ticket: String,
    val assets: List<ConnectionAsset>,
) {
    val usableAssets: List<ConnectionAsset>
        get() = assets.filter { it.carriesDevices }

    fun webSocketUrl(): String = "wss://$host/ws?authcode=$ticket&ack=false"
}

data class PanelDevice(
    val zid: String,
    val assetUuid: String,
    val deviceType: String,
    val name: String?,
    val mode: AlarmMode?,
    val transitionDelayEndTimestamp: Long?,
) {
    val isSecurityPanel: Boolean
        get() = deviceType == SECURITY_PANEL_TYPE

    val isTransitioning: Boolean
        get() = transitionDelayEndTimestamp != null

    val transitionEndsAtEpochMillis: Long?
        get() = transitionDelayEndTimestamp?.let {
            if (it < EPOCH_SECONDS_CEILING) it * 1000 else it
        }

    companion object {
        const val SECURITY_PANEL_TYPE: String = "security-panel"
        const val EPOCH_SECONDS_CEILING: Long = 100_000_000_000
    }
}

data class PanelSnapshot(
    val zid: String,
    val assetUuid: String,
    val name: String?,
    val mode: AlarmMode?,
    val transitioning: Boolean,
    val transitionEndsAtEpochMillis: Long?,
)

sealed interface PanelResult {
    data class Ready(val snapshot: PanelSnapshot) : PanelResult

    data object NoSecurityPanel : PanelResult

    data object NoUsableAsset : PanelResult

    data class Rejected(val requested: AlarmMode, val observed: AlarmMode?) : PanelResult

    data class TimedOut(val stage: String) : PanelResult

    data class Unreachable(val statusCode: Int?, val diagnostic: String) : PanelResult
}
