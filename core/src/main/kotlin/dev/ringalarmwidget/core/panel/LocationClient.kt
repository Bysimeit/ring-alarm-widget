package dev.ringalarmwidget.core.panel

import dev.ringalarmwidget.core.net.RingJson
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

sealed interface Fetch<out T> {
    data class Ok<T>(val value: T) : Fetch<T>
    data class Failed(val statusCode: Int?, val diagnostic: String) : Fetch<Nothing>
}

private const val DIAGNOSTIC_LENGTH = 300

class LocationClient(
    private val http: HttpClient,
    private val hardwareId: String,
) {

    suspend fun locations(accessToken: String): Fetch<List<RingLocation>> {
        val response = try {
            http.get(LOCATIONS_URL) { authorize(accessToken) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            return Fetch.Failed(null, failure.describe())
        }

        val raw = response.bodyAsText()
        if (!response.status.isSuccess()) {
            return Fetch.Failed(response.status.value, raw.take(DIAGNOSTIC_LENGTH))
        }

        val payload = runCatching { RingJson.decodeFromString<LocationsResponse>(raw) }.getOrNull()
            ?: return Fetch.Failed(response.status.value, raw.take(DIAGNOSTIC_LENGTH))

        return Fetch.Ok(payload.userLocations.map { RingLocation(id = it.locationId, name = it.name) })
    }

    suspend fun ticket(accessToken: String, locationId: String): Fetch<ConnectionTicket> {
        val response = try {
            http.get(ticketUrl(locationId)) { authorize(accessToken) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            return Fetch.Failed(null, failure.describe())
        }

        val raw = response.bodyAsText()
        if (!response.status.isSuccess()) {
            return Fetch.Failed(response.status.value, raw.take(DIAGNOSTIC_LENGTH))
        }

        val payload = runCatching { RingJson.decodeFromString<TicketResponse>(raw) }.getOrNull()
            ?: return Fetch.Failed(response.status.value, raw.take(DIAGNOSTIC_LENGTH))

        return Fetch.Ok(
            ConnectionTicket(
                host = payload.host,
                ticket = payload.ticket,
                assets = payload.assets.map {
                    ConnectionAsset(uuid = it.uuid, kind = it.kind, status = it.status)
                },
            )
        )
    }

    private fun HttpRequestBuilder.authorize(accessToken: String) {
        header(HttpHeaders.Authorization, "Bearer $accessToken")
        header(HEADER_HARDWARE_ID, hardwareId)
    }

    companion object {
        const val LOCATIONS_URL: String = "https://api.ring.com/devices/v1/locations"
        const val TICKET_BASE_URL: String =
            "https://prd-api-us.prd.rings.solutions/api/v1/clap/tickets"

        private const val HEADER_HARDWARE_ID = "hardware_id"

        fun ticketUrl(locationId: String): String =
            "$TICKET_BASE_URL?locationID=$locationId" +
                "&enableExtendedEmergencyCellUsage=true&requestedTransport=ws"
    }
}

internal fun Exception.describe(): String =
    message ?: this::class.simpleName ?: "unknown failure"
