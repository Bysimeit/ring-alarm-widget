package dev.ringalarmwidget.core.auth

sealed interface Authorized<out T> {
    data class Done<T>(val value: T) : Authorized<T>
    data object SignedOut : Authorized<Nothing>
    data class Unavailable(val cause: LeaseFailure) : Authorized<Nothing>
}

suspend fun <T> SessionManager.authorize(
    rejected: (T) -> Boolean,
    block: suspend (String) -> T,
): Authorized<T> {
    var lease = accessToken()
    var renewed = false

    while (true) {
        val token = when (val current = lease) {
            is TokenLease.Active -> current.accessToken
            TokenLease.SignedOut -> return Authorized.SignedOut
            is TokenLease.Unavailable -> return Authorized.Unavailable(current.cause)
        }

        val value = block(token)
        if (renewed || !rejected(value)) return Authorized.Done(value)

        renewed = true
        lease = renew(token)
    }
}
