package dev.ringalarmwidget.core.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

const val CLIENT_USER_AGENT: String = "android:com.ringapp"

val RingJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    explicitNulls = false
}

fun ringHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    expectSuccess = false

    install(ContentNegotiation) {
        json(RingJson)
    }

    install(WebSockets)

    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 20_000
        socketTimeoutMillis = 20_000
    }

    defaultRequest {
        header(HttpHeaders.UserAgent, CLIENT_USER_AGENT)
    }
}
