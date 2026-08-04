package dev.ringalarmwidget.core.auth

sealed interface AuthResult {
    data class Success(val session: Session) : AuthResult

    data class TwoFactorRequired(val prompt: TwoFactorPrompt) : AuthResult

    data class RateLimited(val retryAfterSeconds: Long?) : AuthResult

    data object InvalidCredentials : AuthResult

    data object InvalidTwoFactorCode : AuthResult

    data class Failed(val statusCode: Int?, val diagnostic: String) : AuthResult
}

data class TwoFactorPrompt(
    val delivery: TwoFactorDelivery,
    val maskedDestination: String?,
    val retryAfterSeconds: Long?,
)

enum class TwoFactorDelivery {
    SMS,
    EMAIL,
    AUTHENTICATOR,
    UNKNOWN;

    companion object {
        fun fromTsvState(raw: String?): TwoFactorDelivery = when (raw?.lowercase()) {
            "sms" -> SMS
            "email" -> EMAIL
            "totp" -> AUTHENTICATOR
            else -> UNKNOWN
        }
    }
}
