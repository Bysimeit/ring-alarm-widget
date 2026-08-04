package dev.ringalarmwidget.core.panel

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import kotlinx.coroutines.CancellationException

data class PanelTimeouts(
    val deviceListMillis: Long = 15_000,
    val modeConfirmationMillis: Long = 20_000,
)

class PanelClient(
    private val http: HttpClient,
    private val locationClient: LocationClient,
    private val timeouts: PanelTimeouts = PanelTimeouts(),
) {

    suspend fun readMode(accessToken: String, locationId: String): PanelResult =
        withSecurityPanel(accessToken, locationId) { _, panel ->
            PanelResult.Ready(panel.snapshot())
        }

    suspend fun setMode(
        accessToken: String,
        locationId: String,
        mode: AlarmMode,
        bypassZids: List<String> = emptyList(),
    ): PanelResult = withSecurityPanel(accessToken, locationId) { connection, panel ->
        if (panel.mode == mode && !panel.isTransitioning) {
            return@withSecurityPanel PanelResult.Ready(panel.snapshot())
        }

        connection.switchMode(panel.assetUuid, panel.zid, mode, bypassZids)

        val watch = connection.awaitPanelUpdate(panel.zid, timeouts.modeConfirmationMillis) {
            it.mode == mode
        }

        interpretModeChange(mode, watch)
    }

    private suspend fun withSecurityPanel(
        accessToken: String,
        locationId: String,
        block: suspend (PanelConnection, PanelDevice) -> PanelResult,
    ): PanelResult {
        val ticket = when (val fetched = locationClient.ticket(accessToken, locationId)) {
            is Fetch.Failed -> return PanelResult.Unreachable(fetched.statusCode, fetched.diagnostic)
            is Fetch.Ok -> fetched.value
        }

        val assets = ticket.usableAssets
        if (assets.isEmpty()) return PanelResult.NoUsableAsset

        var outcome: PanelResult = PanelResult.TimedOut(STAGE_CONNECT)

        try {
            http.webSocket(urlString = ticket.webSocketUrl()) {
                val connection = PanelConnection(this)
                outcome = when (val lookup = connection.findSecurityPanel(assets, timeouts.deviceListMillis)) {
                    is PanelLookup.Found -> block(connection, lookup.device)
                    PanelLookup.Absent -> PanelResult.NoSecurityPanel
                    PanelLookup.TimedOut -> PanelResult.TimedOut(STAGE_DEVICE_LIST)
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            return PanelResult.Unreachable(null, failure.describe())
        }

        return outcome
    }

    companion object {
        private const val STAGE_CONNECT = "connect"
        private const val STAGE_DEVICE_LIST = "device-list"
    }
}

internal fun interpretModeChange(requested: AlarmMode, watch: PanelWatch): PanelResult = when {
    watch.matched != null -> PanelResult.Ready(watch.matched.snapshot())
    watch.lastSeen != null -> PanelResult.Rejected(requested, watch.lastSeen.mode)
    else -> PanelResult.TimedOut(STAGE_MODE_CONFIRMATION)
}

internal const val STAGE_MODE_CONFIRMATION = "mode-confirmation"

internal fun PanelDevice.snapshot(): PanelSnapshot = PanelSnapshot(
    zid = zid,
    assetUuid = assetUuid,
    name = name,
    mode = mode,
    transitioning = isTransitioning,
    transitionEndsAtEpochMillis = transitionEndsAtEpochMillis,
)
