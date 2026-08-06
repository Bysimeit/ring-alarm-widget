package dev.ringalarmwidget.core.panel

import dev.ringalarmwidget.core.net.RingJson
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject

internal data class PanelFrame(
    val channel: String?,
    val message: IncomingMessage,
)

internal data class PanelWatch(
    val matched: PanelDevice?,
    val lastSeen: PanelDevice?,
    val bypassRequired: Boolean = false,
)

internal sealed interface PanelLookup {
    data class Found(val device: PanelDevice, val faulted: List<FaultedSensor>) : PanelLookup
    data object Absent : PanelLookup
    data object TimedOut : PanelLookup
}

internal class PanelConnection(
    private val session: DefaultClientWebSocketSession,
) {
    private var seq = 1

    suspend fun requestDeviceList(assetUuid: String) {
        send(OutgoingMessage(msg = MSG_DEVICE_LIST, dst = assetUuid, seq = seq++))
    }

    suspend fun switchMode(
        assetUuid: String,
        zid: String,
        mode: AlarmMode,
        bypassZids: List<String>,
    ) {
        send(
            OutgoingMessage(
                msg = MSG_DEVICE_SET,
                datatype = DATATYPE_DEVICE_SET,
                dst = assetUuid,
                body = buildSwitchModeBody(zid, mode, bypassZids),
                seq = seq++,
            )
        )
    }

    suspend fun findSecurityPanel(
        assets: List<ConnectionAsset>,
        timeoutMillis: Long,
    ): PanelLookup {
        assets.forEach { requestDeviceList(it.uuid) }
        val answered = mutableSetOf<String>()

        val outcome = withTimeoutOrNull(timeoutMillis) {
            var result: PanelLookup? = null
            while (result == null) {
                val frame = nextFrame()
                if (frame == null) {
                    result = PanelLookup.Absent
                    continue
                }
                if (frame.message.msg != MSG_DEVICE_LIST) continue

                frame.message.src?.let { answered += it }
                val devices = frame.devices()
                val panel = devices.firstOrNull { it.isSecurityPanel }

                result = when {
                    panel != null -> PanelLookup.Found(panel, devices.faultedSensors())
                    answered.size >= assets.size -> PanelLookup.Absent
                    else -> null
                }
            }
            result
        }

        return outcome ?: PanelLookup.TimedOut
    }

    suspend fun awaitPanelUpdate(
        zid: String,
        timeoutMillis: Long,
        accept: (PanelDevice) -> Boolean,
    ): PanelWatch {
        var lastSeen: PanelDevice? = null
        var refused = false

        val matched = withTimeoutOrNull(timeoutMillis) {
            var found: PanelDevice? = null
            while (found == null) {
                val frame = nextFrame() ?: break

                if (announcesBypassRequired(frame.message)) {
                    refused = true
                    break
                }

                if (frame.message.datatype != DATATYPE_DEVICE_DOC) continue

                val device = frame.devices().firstOrNull { it.zid == zid } ?: continue
                if (device.mode != null) lastSeen = device
                if (accept(device)) found = device
            }
            found
        }

        return PanelWatch(matched = matched, lastSeen = lastSeen, bypassRequired = refused)
    }

    private suspend fun send(message: OutgoingMessage) {
        session.send(Frame.Text(RingJson.encodeToString(OutgoingEnvelope(msg = message))))
    }

    private suspend fun nextFrame(): PanelFrame? {
        while (true) {
            val frame = try {
                session.incoming.receive()
            } catch (closed: ClosedReceiveChannelException) {
                return null
            }

            if (frame !is Frame.Text) continue

            val envelope = runCatching {
                RingJson.decodeFromString<IncomingEnvelope>(frame.readText())
            }.getOrNull() ?: continue

            val message = envelope.msg ?: continue
            if (message.datatype == DATATYPE_HUB_DISCONNECTION) return null

            return PanelFrame(channel = envelope.channel, message = message)
        }
    }
}

internal fun PanelFrame.devices(): List<PanelDevice> {
    val source = message.src.orEmpty()
    return message.body.orEmpty().mapNotNull { element ->
        val doc = element as? JsonObject ?: return@mapNotNull null
        flattenDeviceDoc(doc).toPanelDevice(source)
    }
}

internal fun List<PanelDevice>.faultedSensors(): List<FaultedSensor> =
    filter { it.isFaultedSensor }.map { FaultedSensor(zid = it.zid, name = it.name) }
