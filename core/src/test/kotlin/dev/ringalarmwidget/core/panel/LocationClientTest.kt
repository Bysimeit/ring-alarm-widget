package dev.ringalarmwidget.core.panel

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
import kotlin.test.assertTrue

class LocationClientTest {

    private val jsonHeaders = headersOf("Content-Type", "application/json")
    private var lastRequest: HttpRequestData? = null

    private fun clientReturning(status: HttpStatusCode, body: String): LocationClient {
        val engine = MockEngine { request ->
            lastRequest = request
            respond(content = body, status = status, headers = jsonHeaders)
        }
        return LocationClient(ringHttpClient(engine), hardwareId = "hw-fixed")
    }

    @Test
    fun `les locations sont extraites de user_locations`() = runTest {
        val client = clientReturning(
            HttpStatusCode.OK,
            """{"user_locations":[{"location_id":"loc-1","name":"Maison"},{"location_id":"loc-2","name":"Bureau"}]}""",
        )

        val result = client.locations("token")

        val ok = assertIs<Fetch.Ok<List<RingLocation>>>(result)
        assertEquals(listOf("loc-1", "loc-2"), ok.value.map { it.id })
        assertEquals("Maison", ok.value.first().name)
    }

    @Test
    fun `le jeton et le hardware id accompagnent la requete`() = runTest {
        val client = clientReturning(HttpStatusCode.OK, """{"user_locations":[]}""")

        client.locations("token-abc")

        val headers = requireNotNull(lastRequest).headers
        assertEquals("Bearer token-abc", headers["Authorization"])
        assertEquals("hw-fixed", headers["hardware_id"])
    }

    @Test
    fun `un compte sans location renvoie une liste vide`() = runTest {
        val client = clientReturning(HttpStatusCode.OK, """{"user_locations":[]}""")

        val ok = assertIs<Fetch.Ok<List<RingLocation>>>(client.locations("token"))
        assertTrue(ok.value.isEmpty())
    }

    @Test
    fun `le ticket porte l hote et les assets`() = runTest {
        val client = clientReturning(
            HttpStatusCode.OK,
            """{"host":"ws.example","ticket":"tkt","assets":[{"uuid":"a","kind":"base_station_v1","status":"online"}]}""",
        )

        val ok = assertIs<Fetch.Ok<ConnectionTicket>>(client.ticket("token", "loc-1"))

        assertEquals("ws.example", ok.value.host)
        assertEquals("tkt", ok.value.ticket)
        assertEquals(1, ok.value.usableAssets.size)
    }

    @Test
    fun `l url du ticket cible la bonne location et le transport ws`() {
        val url = LocationClient.ticketUrl("loc-42")

        assertTrue(url.startsWith(LocationClient.TICKET_BASE_URL))
        assertTrue(url.contains("locationID=loc-42"))
        assertTrue(url.contains("requestedTransport=ws"))
    }

    @Test
    fun `un jeton refuse remonte le statut`() = runTest {
        val client = clientReturning(HttpStatusCode.Unauthorized, """{"error":"unauthorized"}""")

        val failed = assertIs<Fetch.Failed>(client.locations("perime"))

        assertEquals(401, failed.statusCode)
    }

    @Test
    fun `une reponse illisible ne fait pas planter`() = runTest {
        val client = clientReturning(HttpStatusCode.OK, "<html>maintenance</html>")

        val failed = assertIs<Fetch.Failed>(client.locations("token"))

        assertTrue(failed.diagnostic.contains("maintenance"))
    }
}
