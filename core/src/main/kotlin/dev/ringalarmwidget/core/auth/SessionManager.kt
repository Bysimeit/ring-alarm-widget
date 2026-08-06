package dev.ringalarmwidget.core.auth

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface LeaseFailure {
    data class RateLimited(val retryAfterSeconds: Long?) : LeaseFailure
    data class Unreachable(val statusCode: Int?, val diagnostic: String) : LeaseFailure
}

sealed interface TokenLease {
    data class Active(val accessToken: String) : TokenLease
    data object SignedOut : TokenLease
    data class Unavailable(val cause: LeaseFailure) : TokenLease
}

class SessionManager(
    private val refresher: TokenRefresher,
    private val store: TokenStore,
    private val nowEpochSeconds: () -> Long,
) {
    private val mutex = Mutex()

    suspend fun accessToken(): TokenLease = mutex.withLock {
        val session = store.load() ?: return@withLock TokenLease.SignedOut

        if (!session.needsRefresh(nowEpochSeconds())) {
            return@withLock TokenLease.Active(session.accessToken)
        }

        rotate(session)
    }

    suspend fun renew(rejectedAccessToken: String): TokenLease = mutex.withLock {
        val session = store.load() ?: return@withLock TokenLease.SignedOut

        if (session.accessToken != rejectedAccessToken) {
            return@withLock TokenLease.Active(session.accessToken)
        }

        rotate(session)
    }

    private suspend fun rotate(session: Session): TokenLease =
        when (val result = refresher.refresh(session.refreshToken)) {
            is AuthResult.Success -> {
                store.save(result.session)
                TokenLease.Active(result.session.accessToken)
            }

            AuthResult.InvalidCredentials,
            AuthResult.InvalidTwoFactorCode,
            is AuthResult.TwoFactorRequired -> {
                store.clear()
                TokenLease.SignedOut
            }

            is AuthResult.RateLimited ->
                TokenLease.Unavailable(LeaseFailure.RateLimited(result.retryAfterSeconds))

            is AuthResult.Failed ->
                TokenLease.Unavailable(LeaseFailure.Unreachable(result.statusCode, result.diagnostic))
        }

    suspend fun adopt(session: Session): Unit = mutex.withLock {
        store.save(session)
    }

    suspend fun signOut(): Unit = mutex.withLock {
        store.clear()
    }
}
