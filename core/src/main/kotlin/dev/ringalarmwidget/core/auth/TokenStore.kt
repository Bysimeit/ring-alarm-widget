package dev.ringalarmwidget.core.auth

interface TokenStore {
    suspend fun load(): Session?
    suspend fun save(session: Session)
    suspend fun clear()
}

fun interface TokenRefresher {
    suspend fun refresh(refreshToken: String): AuthResult
}
