package dev.ringalarmwidget.core.panel

import dev.ringalarmwidget.core.net.RingJson
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PanelWireTest {

    private fun doc(raw: String) = RingJson.decodeFromString<JsonObject>(raw)

    @Test
    fun `correspondance des modes avec l API`() {
        assertEquals("none", AlarmMode.DISARMED.wireValue)
        assertEquals("some", AlarmMode.HOME.wireValue)
        assertEquals("all", AlarmMode.AWAY.wireValue)

        assertEquals(AlarmMode.DISARMED, AlarmMode.fromWire("none"))
        assertEquals(AlarmMode.HOME, AlarmMode.fromWire("some"))
        assertEquals(AlarmMode.AWAY, AlarmMode.fromWire("all"))
        assertNull(AlarmMode.fromWire("heat"))
        assertNull(AlarmMode.fromWire(null))
    }

    @Test
    fun `le document appareil fusionne general v2 et device v1`() {
        val flattened = flattenDeviceDoc(
            doc(
                """
                {
                  "general": { "v2": { "zid": "abc", "deviceType": "security-panel", "name": "Panneau" } },
                  "device": { "v1": { "mode": "some", "transitionDelayEndTimestamp": 1754000000000 } }
                }
                """
            )
        )

        val device = flattened.toPanelDevice(assetUuid = "asset-1")

        requireNotNull(device)
        assertEquals("abc", device.zid)
        assertEquals("security-panel", device.deviceType)
        assertEquals("Panneau", device.name)
        assertEquals(AlarmMode.HOME, device.mode)
        assertTrue(device.isSecurityPanel)
        assertTrue(device.isTransitioning)
    }

    @Test
    fun `la fin de transition est ramenee en millisecondes`() {
        val base = PanelDevice(
            zid = "z",
            assetUuid = "asset-1",
            deviceType = PanelDevice.SECURITY_PANEL_TYPE,
            name = null,
            mode = AlarmMode.AWAY,
            transitionDelayEndTimestamp = null,
        )

        assertNull(base.transitionEndsAtEpochMillis)
        assertEquals(
            1_754_000_000_000,
            base.copy(transitionDelayEndTimestamp = 1_754_000_000_000).transitionEndsAtEpochMillis,
        )
        assertEquals(
            1_754_000_000_000,
            base.copy(transitionDelayEndTimestamp = 1_754_000_000).transitionEndsAtEpochMillis,
        )
    }

    @Test
    fun `l instantane transporte la fin de transition`() {
        val device = PanelDevice(
            zid = "z",
            assetUuid = "asset-1",
            deviceType = PanelDevice.SECURITY_PANEL_TYPE,
            name = null,
            mode = AlarmMode.AWAY,
            transitionDelayEndTimestamp = 1_754_000_000_000,
        )

        val snapshot = device.snapshot()

        assertTrue(snapshot.transitioning)
        assertEquals(1_754_000_000_000, snapshot.transitionEndsAtEpochMillis)
    }

    @Test
    fun `un appareil sans zid est ignore`() {
        val flattened = flattenDeviceDoc(doc("""{ "device": { "v1": { "mode": "all" } } }"""))

        assertNull(flattened.toPanelDevice(assetUuid = "asset-1"))
    }

    @Test
    fun `absence de delai de transition signale un etat stable`() {
        val flattened = flattenDeviceDoc(
            doc("""{ "general": { "v2": { "zid": "z", "deviceType": "security-panel" } } }""")
        )

        val device = requireNotNull(flattened.toPanelDevice("asset-1"))
        assertFalse(device.isTransitioning)
        assertNull(device.mode)
    }

    @Test
    fun `la commande de changement de mode a la forme attendue`() {
        val body = buildSwitchModeBody("panel-zid", AlarmMode.AWAY, emptyList())
        val envelope = OutgoingEnvelope(
            msg = OutgoingMessage(
                msg = MSG_DEVICE_SET,
                datatype = DATATYPE_DEVICE_SET,
                dst = "asset-uuid",
                body = body,
                seq = 3,
            )
        )

        assertEquals(
            """{"channel":"message","msg":{"msg":"DeviceInfoSet","datatype":"DeviceInfoSetType",""" +
                """"dst":"asset-uuid","body":[{"zid":"panel-zid","command":{"v1":[{"commandType":""" +
                """"security-panel.switch-mode","data":{"mode":"all"}}]}}],"seq":3}}""",
            RingJson.encodeToString(envelope),
        )
    }

    @Test
    fun `le bypass n est envoye que s il est demande`() {
        val withoutBypass = RingJson.encodeToString(buildSwitchModeBody("z", AlarmMode.HOME, emptyList()))
        assertFalse(withoutBypass.contains("bypass"))

        val withBypass = RingJson.encodeToString(buildSwitchModeBody("z", AlarmMode.HOME, listOf("s1", "s2")))
        assertTrue(withBypass.contains(""""bypass":["s1","s2"]"""))
    }

    @Test
    fun `seuls les assets porteurs d appareils sont retenus`() {
        val ticket = ConnectionTicket(
            host = "example.rings.solutions",
            ticket = "tkt",
            assets = listOf(
                ConnectionAsset("a", "base_station_v1", "online"),
                ConnectionAsset("b", "beams_bridge_v1", "online"),
                ConnectionAsset("c", "doorbell_v5", "online"),
            ),
        )

        assertEquals(listOf("a", "b"), ticket.usableAssets.map { it.uuid })
    }

    @Test
    fun `l url websocket porte le ticket et desactive les accuses`() {
        val ticket = ConnectionTicket("host.example", "tkt-123", emptyList())

        assertEquals("wss://host.example/ws?authcode=tkt-123&ack=false", ticket.webSocketUrl())
    }
}
