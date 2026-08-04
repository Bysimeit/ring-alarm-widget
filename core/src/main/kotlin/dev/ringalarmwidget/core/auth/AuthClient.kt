package dev.ringalarmwidget.core.auth

import dev.ringalarmwidget.core.net.RingJson
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class AuthClient(
    private val http: HttpClient,
    private val hardwareId: String,
    private val nowEpochSeconds: () -> Long,
    private val clientId: String = DEFAULT_CLIENT_ID,
) : TokenRefresher {

    suspend fun signIn(
        email: String,
        password: String,
        twoFactorCode: String? = null,
    ): AuthResult {
        val response = http.post(OAUTH_TOKEN_URL) {
            contentType(ContentType.Application.Json)
            header(HEADER_HARDWARE_ID, hardwareId)
            header(HEADER_TWO_FACTOR_SUPPORT, "true")
            if (twoFactorCode != null) {
                header(HEADER_TWO_FACTOR_CODE, twoFactorCode)
            }
            setBody(
                PasswordGrantBody(
                    clientId = clientId,
                    username = email,
                    password = password,
                )
            )
        }
        return interpret(response, twoFactorCodeSubmitted = twoFactorCode != null)
    }

    override suspend fun refresh(refreshToken: String): AuthResult {
        val response = http.post(OAUTH_TOKEN_URL) {
            contentType(ContentType.Application.Json)
            header(HEADER_HARDWARE_ID, hardwareId)
            header(HEADER_TWO_FACTOR_SUPPORT, "true")
            setBody(
                RefreshGrantBody(
                    clientId = clientId,
                    refreshToken = refreshToken,
                )
            )
        }
        return interpret(response, twoFactorCodeSubmitted = false)
    }

    private suspend fun interpret(
        response: HttpResponse,
        twoFactorCodeSubmitted: Boolean,
    ): AuthResult {
        val status = response.status.value
        val raw = response.bodyAsText()

        if (response.status.isSuccess()) {
            val payload = runCatching { RingJson.decodeFromString<TokenPayload>(raw) }.getOrNull()
                ?: return AuthResult.Failed(status, raw.take(DIAGNOSTIC_LENGTH))
            return AuthResult.Success(
                Session(
                    accessToken = payload.accessToken,
                    refreshToken = payload.refreshToken,
                    expiresAtEpochSeconds = nowEpochSeconds() + payload.expiresIn,
                )
            )
        }

        val challenge = runCatching { RingJson.decodeFromString<ChallengePayload>(raw) }.getOrNull()
        val retryAfter = challenge?.nextTimeInSecs
            ?: response.headers[HEADER_RETRY_AFTER]?.toLongOrNull()

        return when {
            status == STATUS_TOO_MANY_REQUESTS -> AuthResult.RateLimited(retryAfter)

            status == STATUS_PRECONDITION_FAILED -> AuthResult.TwoFactorRequired(promptOf(challenge, retryAfter))

            challenge?.tsvState != null && !twoFactorCodeSubmitted ->
                AuthResult.TwoFactorRequired(promptOf(challenge, retryAfter))

            twoFactorCodeSubmitted && status == STATUS_BAD_REQUEST -> AuthResult.InvalidTwoFactorCode

            status == STATUS_BAD_REQUEST || status == STATUS_UNAUTHORIZED -> AuthResult.InvalidCredentials

            else -> AuthResult.Failed(status, challenge?.errorDescription ?: raw.take(DIAGNOSTIC_LENGTH))
        }
    }

    private fun promptOf(challenge: ChallengePayload?, retryAfter: Long?) = TwoFactorPrompt(
        delivery = TwoFactorDelivery.fromTsvState(challenge?.tsvState),
        maskedDestination = challenge?.phone,
        retryAfterSeconds = retryAfter,
    )

    companion object {
        const val DEFAULT_CLIENT_ID: String = "ring_official_android"
        const val OAUTH_TOKEN_URL: String = "https://oauth.ring.com/oauth/token"

        private const val HEADER_HARDWARE_ID = "hardware_id"
        private const val HEADER_TWO_FACTOR_SUPPORT = "2fa-support"
        private const val HEADER_TWO_FACTOR_CODE = "2fa-code"
        private const val HEADER_RETRY_AFTER = "Retry-After"
        private const val DIAGNOSTIC_LENGTH = 300

        private const val STATUS_BAD_REQUEST = 400
        private const val STATUS_UNAUTHORIZED = 401
        private const val STATUS_PRECONDITION_FAILED = 412
        private const val STATUS_TOO_MANY_REQUESTS = 429
    }
}
