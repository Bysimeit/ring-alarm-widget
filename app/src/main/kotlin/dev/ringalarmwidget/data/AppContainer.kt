package dev.ringalarmwidget.data

import android.content.Context
import dev.ringalarmwidget.core.auth.AuthClient
import dev.ringalarmwidget.core.auth.SessionManager
import dev.ringalarmwidget.core.net.ringHttpClient
import dev.ringalarmwidget.core.panel.LocationClient
import dev.ringalarmwidget.core.panel.PanelClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000

class AppContainer private constructor(context: Context) {

    val store = AndroidTokenStore(context)

    private val http = ringHttpClient(OkHttp.create())
    private val mutex = Mutex()

    private var manager: SessionManager? = null

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
