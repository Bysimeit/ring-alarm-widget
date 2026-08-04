package dev.ringalarmwidget.spike

import dev.ringalarmwidget.core.auth.Session
import dev.ringalarmwidget.core.auth.TokenStore
import dev.ringalarmwidget.core.net.RingJson
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

@Serializable
private data class SpikeState(
    val hardwareId: String,
    val session: Session? = null,
    val locationId: String? = null,
)

class FileTokenStore(private val file: File) : TokenStore {

    private var state: SpikeState = readOrCreate()

    val hardwareId: String get() = state.hardwareId

    var locationId: String?
        get() = state.locationId
        set(value) = write(state.copy(locationId = value))

    override suspend fun load(): Session? = state.session

    override suspend fun save(session: Session) = write(state.copy(session = session))

    override suspend fun clear() = write(state.copy(session = null))

    private fun readOrCreate(): SpikeState {
        if (file.exists()) {
            val parsed = runCatching {
                RingJson.decodeFromString<SpikeState>(file.readText())
            }.getOrNull()
            if (parsed != null) return parsed
        }
        val fresh = SpikeState(hardwareId = UUID.randomUUID().toString())
        persist(fresh)
        return fresh
    }

    private fun write(next: SpikeState) {
        persist(next)
        state = next
    }

    private fun persist(value: SpikeState) {
        file.writeText(RingJson.encodeToString(value))
    }
}
