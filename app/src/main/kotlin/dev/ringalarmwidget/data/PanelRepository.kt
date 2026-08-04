package dev.ringalarmwidget.data

import android.content.Context
import dev.ringalarmwidget.core.auth.LeaseFailure
import dev.ringalarmwidget.core.auth.TokenLease
import dev.ringalarmwidget.core.panel.AlarmMode
import dev.ringalarmwidget.core.panel.Fetch
import dev.ringalarmwidget.core.panel.PanelResult
import dev.ringalarmwidget.core.panel.PanelSnapshot

sealed interface PanelOutcome {
    data class Ready(val locationName: String?, val snapshot: PanelSnapshot) : PanelOutcome
    data object SignedOut : PanelOutcome
    data object NoLocation : PanelOutcome
    data class Unavailable(val cause: LeaseFailure) : PanelOutcome
    data class Failed(val result: PanelResult) : PanelOutcome
}

class PanelRepository(context: Context) {

    private val container = AppContainer.get(context)

    val store: AndroidTokenStore get() = container.store

    suspend fun read(): PanelOutcome = settle(
        withLocation { token, locationId -> container.panel().readMode(token, locationId) }
    )

    suspend fun set(mode: AlarmMode): PanelOutcome = settle(
        withLocation { token, locationId -> container.panel().setMode(token, locationId, mode) }
    )

    private suspend fun withLocation(block: suspend (String, String) -> PanelResult): PanelOutcome {
        val token = when (val lease = container.sessionManager().accessToken()) {
            is TokenLease.Active -> lease.accessToken
            TokenLease.SignedOut -> return PanelOutcome.SignedOut
            is TokenLease.Unavailable -> return PanelOutcome.Unavailable(lease.cause)
        }

        val cache = store.panelCache()

        if (cache?.locationName != null) {
            val result = block(token, cache.locationId)
            if (result is PanelResult.Ready) return PanelOutcome.Ready(cache.locationName, result.snapshot)
            if (!warrantsDiscovery(result)) return PanelOutcome.Failed(result)
        }

        val locations = when (val fetched = container.locations().locations(token)) {
            is Fetch.Failed ->
                return PanelOutcome.Failed(PanelResult.Unreachable(fetched.statusCode, fetched.diagnostic))

            is Fetch.Ok -> fetched.value
        }

        if (locations.isEmpty()) return PanelOutcome.NoLocation

        val location = locations.firstOrNull { it.id == cache?.locationId } ?: locations.first()
        store.setLocation(location.id, location.name)

        return when (val result = block(token, location.id)) {
            is PanelResult.Ready -> PanelOutcome.Ready(location.name, result.snapshot)
            else -> PanelOutcome.Failed(result)
        }
    }

    private suspend fun settle(outcome: PanelOutcome): PanelOutcome {
        store.setPendingMode(null)
        store.setLastAttemptFailed(
            outcome is PanelOutcome.Failed ||
                outcome is PanelOutcome.Unavailable ||
                outcome is PanelOutcome.NoLocation
        )
        if (outcome is PanelOutcome.Ready) {
            store.setCachedMode(outcome.snapshot.mode)
            store.setTransitionEndsAt(outcome.snapshot.transitionEndsAtEpochMillis)
        }
        return outcome
    }

    private fun warrantsDiscovery(result: PanelResult): Boolean = when (result) {
        is PanelResult.Unreachable -> result.statusCode != null
        PanelResult.NoSecurityPanel, PanelResult.NoUsableAsset -> true
        else -> false
    }

}
