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

data class FaultedSensor(
    val zid: String,
    val name: String?,
)

data class PanelDevice(
    val zid: String,
    val assetUuid: String,
    val deviceType: String,
    val name: String?,
    val mode: AlarmMode?,
    val transitionDelayEndTimestamp: Long?,
    val faulted: Boolean = false,
) {
    val isSecurityPanel: Boolean
        get() = deviceType == SECURITY_PANEL_TYPE

    val isFaultedSensor: Boolean
        get() = faulted && deviceType.startsWith(SENSOR_TYPE_PREFIX)

    val isTransitioning: Boolean
        get() = transitionDelayEndTimestamp != null

    val transitionEndsAtEpochMillis: Long?
        get() = transitionDelayEndTimestamp?.let {
            if (it < EPOCH_SECONDS_CEILING) it * 1000 else it
        }

    companion object {
        const val SECURITY_PANEL_TYPE: String = "security-panel"
        const val SENSOR_TYPE_PREFIX: String = "sensor."
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

    data class BypassRequired(
        val requested: AlarmMode,
        val sensors: List<FaultedSensor>,
    ) : PanelResult

    data class TimedOut(val stage: String) : PanelResult

    data class Unreachable(val statusCode: Int?, val diagnostic: String) : PanelResult
}

val PanelResult.rejectsCredentials: Boolean
    get() = this is PanelResult.Unreachable && statusCode in REJECTED_CREDENTIAL_STATUSES

val PanelResult.isTransient: Boolean
    get() = when (this) {
        is PanelResult.Ready -> false
        is PanelResult.Rejected -> false
        is PanelResult.BypassRequired -> false
        PanelResult.NoSecurityPanel, PanelResult.NoUsableAsset -> false
        is PanelResult.TimedOut -> true
        is PanelResult.Unreachable -> !rejectsCredentials
    }

private val REJECTED_CREDENTIAL_STATUSES = setOf(401, 403)
