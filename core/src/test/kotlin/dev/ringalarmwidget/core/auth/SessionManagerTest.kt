package dev.ringalarmwidget.core.auth

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

private class InMemoryTokenStore(initial: Session?) : TokenStore {
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

private class CountingRefresher(
    private val outcome: (String) -> AuthResult,
) : TokenRefresher {
    var calls = 0
        private set

    override suspend fun refresh(refreshToken: String): AuthResult {
        calls++
        delay(50)
        return outcome(refreshToken)
    }
}

class SessionManagerTest {

    private fun session(expiresAt: Long, refreshToken: String = "rt1") = Session(
        accessToken = "at1",
        refreshToken = refreshToken,
        expiresAtEpochSeconds = expiresAt,
    )

    @Test
    fun `jeton encore valide reutilise sans appel reseau`() = runTest {
        val store = InMemoryTokenStore(session(expiresAt = 10_000))
        val refresher = CountingRefresher { AuthResult.Failed(null, "ne devrait pas etre appele") }
        val manager = SessionManager(refresher, store, nowEpochSeconds = { 1_000 })

        val lease = manager.accessToken()

        assertEquals(TokenLease.Active("at1"), lease)
        assertEquals(0, refresher.calls)
    }

    @Test
    fun `jeton dans la marge de securite declenche un refresh`() = runTest {
        val store = InMemoryTokenStore(session(expiresAt = 1_060))
        val refresher = CountingRefresher {
            AuthResult.Success(Session("at2", "rt2", 4_600))
        }
        val manager = SessionManager(refresher, store, nowEpochSeconds = { 1_000 })

        val lease = manager.accessToken()

        assertEquals(TokenLease.Active("at2"), lease)
        assertEquals(1, refresher.calls)
    }

    @Test
    fun `acces concurrents ne provoquent qu un seul refresh`() = runTest {
        val store = InMemoryTokenStore(session(expiresAt = 500))
        val refresher = CountingRefresher {
            AuthResult.Success(Session("at2", "rt2", 4_600))
        }
        val manager = SessionManager(refresher, store, nowEpochSeconds = { 1_000 })

        val leases = List(8) { async { manager.accessToken() } }.awaitAll()

        assertEquals(1, refresher.calls)
        leases.forEach { assertEquals(TokenLease.Active("at2"), it) }
        assertEquals("rt2", store.current?.refreshToken)
    }

    @Test
    fun `nouveau refresh token persiste immediatement`() = runTest {
        val store = InMemoryTokenStore(session(expiresAt = 500))
        val refresher = CountingRefresher {
            AuthResult.Success(Session("at2", "rt-rotated", 4_600))
        }
        val manager = SessionManager(refresher, store, nowEpochSeconds = { 1_000 })

        manager.accessToken()

        assertEquals("rt-rotated", store.current?.refreshToken)
    }

    @Test
    fun `refresh token rejete deconnecte et purge le stockage`() = runTest {
        val store = InMemoryTokenStore(session(expiresAt = 500))
        val refresher = CountingRefresher { AuthResult.InvalidCredentials }
        val manager = SessionManager(refresher, store, nowEpochSeconds = { 1_000 })

        val lease = manager.accessToken()

        assertEquals(TokenLease.SignedOut, lease)
        assertNull(store.current)
    }

    @Test
    fun `panne reseau ne purge pas la session`() = runTest {
        val store = InMemoryTokenStore(session(expiresAt = 500))
        val refresher = CountingRefresher { AuthResult.Failed(503, "service unavailable") }
        val manager = SessionManager(refresher, store, nowEpochSeconds = { 1_000 })

        val lease = manager.accessToken()

        val unavailable = assertIs<TokenLease.Unavailable>(lease)
        assertIs<LeaseFailure.Unreachable>(unavailable.cause)
        assertEquals("rt1", store.current?.refreshToken)
    }

    @Test
    fun `limitation de debit remonte en cause typee`() = runTest {
        val store = InMemoryTokenStore(session(expiresAt = 500))
        val refresher = CountingRefresher { AuthResult.RateLimited(540) }
        val manager = SessionManager(refresher, store, nowEpochSeconds = { 1_000 })

        val unavailable = assertIs<TokenLease.Unavailable>(manager.accessToken())

        assertEquals(LeaseFailure.RateLimited(540), unavailable.cause)
        assertEquals("rt1", store.current?.refreshToken)
    }

    @Test
    fun `absence de session signale une deconnexion`() = runTest {
        val store = InMemoryTokenStore(null)
        val refresher = CountingRefresher { AuthResult.Failed(null, "ne devrait pas etre appele") }
        val manager = SessionManager(refresher, store, nowEpochSeconds = { 1_000 })

        assertEquals(TokenLease.SignedOut, manager.accessToken())
        assertEquals(0, refresher.calls)
    }
}
