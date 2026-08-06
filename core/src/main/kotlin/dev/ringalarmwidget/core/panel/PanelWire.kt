package dev.ringalarmwidget.core.panel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

@Serializable
internal data class LocationsResponse(
    @SerialName("user_locations") val userLocations: List<UserLocationPayload> = emptyList(),
)

@Serializable
internal data class UserLocationPayload(
    @SerialName("location_id") val locationId: String,
    val name: String = "",
)

@Serializable
internal data class TicketResponse(
    val host: String,
    val ticket: String,
    val assets: List<AssetPayload> = emptyList(),
)

@Serializable
internal data class AssetPayload(
    val uuid: String,
    val kind: String = "",
    val status: String = "unknown",
)

@Serializable
internal data class OutgoingEnvelope(
    val channel: String = CHANNEL_MESSAGE,
    val msg: OutgoingMessage,
)

@Serializable
internal data class OutgoingMessage(
    val msg: String,
    val datatype: String? = null,
    val dst: String,
    val body: JsonArray? = null,
    val seq: Int,
)

@Serializable
internal data class IncomingEnvelope(
    val channel: String? = null,
    val msg: IncomingMessage? = null,
)

@Serializable
internal data class IncomingMessage(
    val msg: String? = null,
    val datatype: String? = null,
    val src: String? = null,
    val body: JsonArray? = null,
)

internal const val CHANNEL_MESSAGE = "message"
internal const val CHANNEL_DATA_UPDATE = "DataUpdate"
internal const val MSG_DEVICE_LIST = "DeviceInfoDocGetList"
internal const val MSG_DEVICE_SET = "DeviceInfoSet"
internal const val MSG_PASSTHRU = "Passthru"
internal const val DATATYPE_DEVICE_SET = "DeviceInfoSetType"
internal const val DATATYPE_DEVICE_DOC = "DeviceInfoDocType"
internal const val DATATYPE_HUB_DISCONNECTION = "HubDisconnectionEventType"
internal const val COMMAND_SWITCH_MODE = "security-panel.switch-mode"
internal const val SOUND_BYPASS_REQUIRED = "bypass-required"

internal fun buildSwitchModeBody(
    zid: String,
    mode: AlarmMode,
    bypassZids: List<String>,
): JsonArray = buildJsonArray {
    addJsonObject {
        put("zid", zid)
        putJsonObject("command") {
            putJsonArray("v1") {
                addJsonObject {
                    put("commandType", COMMAND_SWITCH_MODE)
                    putJsonObject("data") {
                        put("mode", mode.wireValue)
                        if (bypassZids.isNotEmpty()) {
                            putJsonArray("bypass") {
                                bypassZids.forEach { add(it) }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun flattenDeviceDoc(doc: JsonObject): JsonObject {
    val general = doc.nested("general", "v2")
    val device = doc.nested("device", "v1")
    return JsonObject(general + device)
}

private fun JsonObject.nested(outer: String, inner: String): Map<String, kotlinx.serialization.json.JsonElement> {
    val outerObject = this[outer] as? JsonObject ?: return emptyMap()
    val innerObject = outerObject[inner] as? JsonObject ?: return emptyMap()
    return innerObject
}

internal fun JsonObject.toPanelDevice(assetUuid: String): PanelDevice? {
    val zid = text("zid") ?: return null
    val deviceType = text("deviceType") ?: return null
    return PanelDevice(
        zid = zid,
        assetUuid = assetUuid,
        deviceType = deviceType,
        name = text("name"),
        mode = AlarmMode.fromWire(text("mode")),
        transitionDelayEndTimestamp = number("transitionDelayEndTimestamp"),
        faulted = flag("faulted") == true,
    )
}

internal fun announcesBypassRequired(message: IncomingMessage): Boolean {
    if (message.msg != MSG_PASSTHRU) return false

    return message.body.orEmpty().any { element ->
        val path = (element as? JsonObject)
            ?.child("data")
            ?.child("event")
            ?.text("path")
        path?.contains(SOUND_BYPASS_REQUIRED) == true
    }
}

private fun JsonObject.child(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonObject.text(key: String): String? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    if (!primitive.isString && primitive.booleanOrNull != null) return null
    return primitive.content.takeIf { it.isNotEmpty() && it != "null" }
}

private fun JsonObject.number(key: String): Long? =
    (this[key] as? JsonPrimitive)?.longOrNull

private fun JsonObject.flag(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.booleanOrNull
