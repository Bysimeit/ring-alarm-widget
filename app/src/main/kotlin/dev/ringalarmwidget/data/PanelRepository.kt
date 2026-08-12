package dev.ringalarmwidget.data

import android.content.Context
import dev.ringalarmwidget.core.auth.Authorized
import dev.ringalarmwidget.core.auth.authorize
import dev.ringalarmwidget.core.panel.AlarmMode
import dev.ringalarmwidget.core.panel.Fetch
import dev.ringalarmwidget.core.panel.PanelResult
import dev.ringalarmwidget.core.panel.PanelSnapshot
import dev.ringalarmwidget.core.panel.rejectsCredentials
import kotlinx.coroutines.sync.withLock

class PanelRepository(context: Context) {

    private val container = AppContainer.get(context)

    val store: AndroidTokenStore get() = container.store

    suspend fun read(): PanelOutcome = container.panelGate.withLock {
        val outcome = withLocation { token, locationId ->
            container.panel().readMode(token, locationId)
        }
        recordRead(outcome)
        outcome
    }

    suspend fun set(
        mode: AlarmMode,
        mayRetry: Boolean = false,
        bypassZids: List<String> = emptyList(),
    ): PanelOutcome = container.panelGate.withLock {
        val attempted = withLocation { token, locationId ->
            container.panel().setMode(token, locationId, mode, bypassZids)
        }
        val outcome = if (attempted.isTransient) confirm(mode, attempted) else attempted
        recordChange(outcome, retrying = mayRetry && outcome.isTransient)
        outcome
    }

    private suspend fun confirm(mode: AlarmMode, attempted: PanelOutcome): PanelOutcome {
        val observed = withLocation { token, locationId ->
            container.panel().readMode(token, locationId)
        }
        return observed.takeIf { it is PanelOutcome.Ready && it.snapshot.mode == mode } ?: attempted
    }

    suspend fun abandonChange() {
        store.setPendingMode(null)
        store.setBypassPrompt(null)
        store.setRefreshing(false)
        store.setLastAttemptFailed(true)
    }

    suspend fun dismissBypass() {
        store.setBypassPrompt(null)
        store.setPendingMode(null)
        store.setRefreshing(false)
        store.setLastAttemptFailed(false)
    }

    private suspend fun withLocation(block: suspend (String, String) -> PanelResult): PanelOutcome {
        val authorized = container.sessionManager().authorize(
            rejected = { it is PanelOutcome.Failed && it.result.rejectsCredentials },
            block = { token -> attempt(token, block) },
        )

        return when (authorized) {
            is Authorized.Done -> authorized.value
            Authorized.SignedOut -> PanelOutcome.SignedOut
            is Authorized.Unavailable -> PanelOutcome.Unavailable(authorized.cause)
        }
    }

    private suspend fun attempt(
        token: String,
        block: suspend (String, String) -> PanelResult,
    ): PanelOutcome {
        val cache = store.panelCache()

        if (cache?.locationName != null) {
            val result = block(token, cache.locationId)
            if (result is PanelResult.Ready) return PanelOutcome.Ready(cache.locationName, result.snapshot)
            if (!warrantsDiscovery(result)) return interpret(result)
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
            else -> interpret(result)
        }
    }

    private fun interpret(result: PanelResult): PanelOutcome = when (result) {
        is PanelResult.BypassRequired -> PanelOutcome.NeedsBypass(result.requested, result.sensors)
        else -> PanelOutcome.Failed(result)
    }

    private suspend fun recordRead(outcome: PanelOutcome) {
        store.setRefreshing(false)
        if (outcome !is PanelOutcome.Ready) return
        store.setLastAttemptFailed(false)
        cache(outcome.snapshot)
    }

    private suspend fun recordChange(outcome: PanelOutcome, retrying: Boolean) {
        if (retrying) return
        store.setRefreshing(false)

        if (outcome is PanelOutcome.NeedsBypass) {
            store.setPendingMode(null)
            store.setLastAttemptFailed(false)
            store.setBypassPrompt(
                BypassPrompt.of(outcome.requested, outcome.sensors, System.currentTimeMillis())
            )
            return
        }

        store.setPendingMode(null)
        store.setBypassPrompt(null)
        store.setLastAttemptFailed(outcome !is PanelOutcome.Ready && outcome !is PanelOutcome.SignedOut)
        if (outcome is PanelOutcome.Ready) cache(outcome.snapshot)
    }

    private suspend fun cache(snapshot: PanelSnapshot) {
        store.setCachedMode(snapshot.mode)
        store.setTransitionEndsAt(snapshot.transitionEndsAtEpochMillis)
        store.setReadAt(System.currentTimeMillis())
    }

    private fun warrantsDiscovery(result: PanelResult): Boolean = when (result) {
        is PanelResult.Unreachable -> result.statusCode != null && !result.rejectsCredentials
        PanelResult.NoSecurityPanel, PanelResult.NoUsableAsset -> true
        else -> false
    }

}
