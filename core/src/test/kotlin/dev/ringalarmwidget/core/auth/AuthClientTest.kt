package dev.ringalarmwidget.core.auth

import dev.ringalarmwidget.core.net.ringHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AuthClientTest {

    private val jsonHeaders = headersOf("Content-Type", "application/json")
    private var lastRequest: HttpRequestData? = null

    private fun clientReturning(status: HttpStatusCode, body: String): AuthClient {
        val engine = MockEngine { request ->
            lastRequest = request
            respond(content = body, status = status, headers = jsonHeaders)
        }
        return AuthClient(
            http = ringHttpClient(engine),
            hardwareId = "hw-fixed",
            nowEpochSeconds = { 1_000 },
        )
    }

    @Test
    fun `jeton obtenu quand Ring repond 200`() = runTest {
        val auth = clientReturning(
            HttpStatusCode.OK,
            """{"access_token":"at","refresh_token":"rt","expires_in":3600}""",
        )

        val result = auth.signIn("a@b.c", "secret")

        val success = assertIs<AuthResult.Success>(result)
        assertEquals("at", success.session.accessToken)
        assertEquals("rt", success.session.refreshToken)
        assertEquals(4_600, success.session.expiresAtEpochSeconds)
    }

    @Test
    fun `en-tetes d authentification envoyes`() = runTest {
        val auth = clientReturning(
            HttpStatusCode.OK,
            """{"access_token":"at","refresh_token":"rt","expires_in":60}""",
        )

        auth.signIn("a@b.c", "secret", twoFactorCode = "123456")

        val headers = requireNotNull(lastRequest).headers
        assertEquals("hw-fixed", headers["hardware_id"])
        assertEquals("true", headers["2fa-support"])
        assertEquals("123456", headers["2fa-code"])
    }

    @Test
    fun `pas de code 2fa envoye quand il est absent`() = runTest {
        val auth = clientReturning(
            HttpStatusCode.OK,
            """{"access_token":"at","refresh_token":"rt","expires_in":60}""",
        )

        auth.signIn("a@b.c", "secret")

        assertNull(requireNotNull(lastRequest).headers["2fa-code"])
    }

    @Test
    fun `412 declenche le defi 2fa avec la methode de livraison`() = runTest {
        val auth = clientReturning(
            HttpStatusCode.PreconditionFailed,
            """{"tsv_state":"sms","phone":"+32 *** ** 45","next_time_in_secs":60}""",
        )

        val result = auth.signIn("a@b.c", "secret")

        val challenge = assertIs<AuthResult.TwoFactorRequired>(result)
        assertEquals(TwoFactorDelivery.SMS, challenge.prompt.delivery)
        assertEquals("+32 *** ** 45", challenge.prompt.maskedDestination)
        assertEquals(60, challenge.prompt.retryAfterSeconds)
    }

    @Test
    fun `429 remonte la limitation de debit`() = runTest {
        val auth = clientReturning(
            HttpStatusCode.TooManyRequests,
            """{"error":"too_many_requests","next_time_in_secs":540}""",
        )

        val result = auth.signIn("a@b.c", "secret")

        assertEquals(AuthResult.RateLimited(540), result)
    }

    @Test
    fun `400 apres soumission d un code signale un code invalide`() = runTest {
        val auth = clientReturning(
            HttpStatusCode.BadRequest,
            """{"error":"invalid_grant","error_description":"Verification Code is invalid or expired"}""",
        )

        val result = auth.signIn("a@b.c", "secret", twoFactorCode = "000000")

        assertEquals(AuthResult.InvalidTwoFactorCode, result)
    }

    @Test
    fun `400 sans code soumis signale des identifiants invalides`() = runTest {
        val auth = clientReturning(
            HttpStatusCode.BadRequest,
            """{"error":"invalid_grant"}""",
        )

        val result = auth.signIn("a@b.c", "mauvais")

        assertEquals(AuthResult.InvalidCredentials, result)
    }

    @Test
    fun `refresh renvoie une nouvelle session`() = runTest {
        val auth = clientReturning(
            HttpStatusCode.OK,
            """{"access_token":"at2","refresh_token":"rt2","expires_in":3600}""",
        )

        val result = auth.refresh("rt1")

        val success = assertIs<AuthResult.Success>(result)
        assertEquals("rt2", success.session.refreshToken)
    }

    @Test
    fun `statut inattendu remonte en echec detaille`() = runTest {
        val auth = clientReturning(HttpStatusCode.InternalServerError, "boom")

        val result = auth.signIn("a@b.c", "secret")

        val failure = assertIs<AuthResult.Failed>(result)
        assertEquals(500, failure.statusCode)
    }
}
