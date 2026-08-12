package dev.ringalarmwidget.data

import android.content.Context
import dev.ringalarmwidget.core.auth.AuthClient
import dev.ringalarmwidget.core.auth.SessionManager
import dev.ringalarmwidget.core.net.ringHttpClient
import dev.ringalarmwidget.core.panel.LocationClient
import dev.ringalarmwidget.core.panel.PanelClient
import io.ktor.client.engine.okhttp.OkHttp
import android.net.ConnectivityManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Dns
import java.net.InetAddress
import java.security.Security

fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000

class AppContainer private constructor(context: Context) {

    init {
        Security.setProperty("networkaddress.cache.negative.ttl", "0")
        Security.setProperty("networkaddress.cache.ttl", "30")
    }

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    val store = AndroidTokenStore(context)

    val panelGate = Mutex()

    @Volatile
    private var http = ringHttpClient(engine())
    private val mutex = Mutex()

    private var manager: SessionManager? = null

    suspend fun renewTransport(): Unit = mutex.withLock {
        val stale = http
        http = ringHttpClient(engine())
        manager = null
        runCatching { stale.close() }
        Unit
    }

    private fun engine() = OkHttp.create {
        config { dns(activeNetworkDns) }
    }

    private val activeNetworkDns = Dns { hostname ->
        onActiveNetwork(hostname) ?: Dns.SYSTEM.lookup(hostname)
    }

    private fun onActiveNetwork(hostname: String): List<InetAddress>? {
        val network = connectivity?.activeNetwork ?: return null
        return runCatching { network.getAllByName(hostname).toList() }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    suspend fun auth(): AuthClient = AuthClient(http, store.hardwareId(), ::nowEpochSeconds)

    suspend fun sessionManager(): SessionManager = mutex.withLock {
        manager ?: SessionManager(auth(), store, ::nowEpochSeconds).also { manager = it }
    }

    suspend fun locations(): LocationClient = LocationClient(http, store.hardwareId())

    suspend fun panel(): PanelClient = PanelClient(http, locations())

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun get(context: Context): AppContainer =
            instance ?: synchronized(this) {
                instance ?: AppContainer(context.applicationContext).also { instance = it }
            }
    }
}
