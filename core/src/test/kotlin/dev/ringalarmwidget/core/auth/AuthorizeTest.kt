package dev.ringalarmwidget.core.auth

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private class ScriptedRefresher(private val outcomes: List<AuthResult>) : TokenRefresher {
    var calls = 0
        private set

    override suspend fun refresh(refreshToken: String): AuthResult {
        val outcome = outcomes.getOrElse(calls) { outcomes.last() }
        calls++
        return outcome
    }
}

private class RecordingStore(initial: Session?) : TokenStore {
    var current: Session? = initial
        private set

    override suspend fun load(): Session? = current
    override suspend fun save(session: Session) {
        current = session
    }

    override suspend fun clear() {
        current = null
    }
}

class AuthorizeTest {

    private fun manager(
        store: RecordingStore,
        refresher: TokenRefresher,
        now: Long = 1_000,
    ) = SessionManager(refresher, store, nowEpochSeconds = { now })

    @Test
    fun `un appel accepte n appelle le bloc qu une fois`() = runTest {
        val store = RecordingStore(Session("at1", "rt1", 10_000))
        val refresher = ScriptedRefresher(listOf(AuthResult.Failed(null, "inattendu")))
        val tokens = mutableListOf<String>()

        val authorized = manager(store, refresher).authorize(rejected = { false }) { token ->
            tokens += token
            "ok"
        }

        assertEquals(Authorized.Done("ok"), authorized)
        assertEquals(listOf("at1"), tokens)
        assertEquals(0, refresher.calls)
    }

    @Test
    fun `un jeton rejete par le serveur declenche un renouvellement et un seul rejeu`() = runTest {
        val store = RecordingStore(Session("at1", "rt1", 10_000))
        val refresher = ScriptedRefresher(listOf(AuthResult.Success(Session("at2", "rt2", 14_000))))
        val tokens = mutableListOf<String>()

        val authorized = manager(store, refresher).authorize(rejected = { it == "rejete" }) { token ->
            tokens += token
            if (token == "at1") "rejete" else "ok"
        }

        assertEquals(Authorized.Done("ok"), authorized)
        assertEquals(listOf("at1", "at2"), tokens)
        assertEquals(1, refresher.calls)
        assertEquals("at2", store.current?.accessToken)
    }

    @Test
    fun `un rejet qui persiste apres renouvellement n entraine pas de boucle`() = runTest {
        val store = RecordingStore(Session("at1", "rt1", 10_000))
        val refresher = ScriptedRefresher(listOf(AuthResult.Success(Session("at2", "rt2", 14_000))))
        val tokens = mutableListOf<String>()

        val authorized = manager(store, refresher).authorize(rejected = { true }) { token ->
            tokens += token
            "rejete"
        }

        assertEquals(Authorized.Done("rejete"), authorized)
        assertEquals(listOf("at1", "at2"), tokens)
        assertEquals(1, refresher.calls)
    }

    @Test
    fun `un refresh token mort deconnecte sans rejouer le bloc`() = runTest {
        val store = RecordingStore(Session("at1", "rt1", 10_000))
        val refresher = ScriptedRefresher(listOf(AuthResult.InvalidCredentials))
        val tokens = mutableListOf<String>()

        val authorized = manager(store, refresher).authorize(rejected = { true }) { token ->
            tokens += token
            "rejete"
        }

        assertEquals(Authorized.SignedOut, authorized)
        assertEquals(listOf("at1"), tokens)
        assertEquals(null, store.current)
    }

    @Test
    fun `un renouvellement injoignable remonte la cause sans purger la session`() = runTest {
        val store = RecordingStore(Session("at1", "rt1", 10_000))
        val refresher = ScriptedRefresher(listOf(AuthResult.Failed(503, "service unavailable")))

        val authorized = manager(store, refresher).authorize(rejected = { true }) { "rejete" }

        val unavailable = assertIs<Authorized.Unavailable>(authorized)
        assertIs<LeaseFailure.Unreachable>(unavailable.cause)
        assertEquals("rt1", store.current?.refreshToken)
    }

    @Test
    fun `sans session le bloc n est jamais appele`() = runTest {
        val store = RecordingStore(null)
        val refresher = ScriptedRefresher(listOf(AuthResult.Failed(null, "inattendu")))
        var calls = 0

        val authorized = manager(store, refresher).authorize(rejected = { false }) {
            calls++
            "ok"
        }

        assertEquals(Authorized.SignedOut, authorized)
        assertEquals(0, calls)
        assertEquals(0, refresher.calls)
    }
}
