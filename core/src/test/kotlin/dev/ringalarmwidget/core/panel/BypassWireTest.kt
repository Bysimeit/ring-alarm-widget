package dev.ringalarmwidget.core.panel

import dev.ringalarmwidget.core.net.RingJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BypassWireTest {

    private fun message(raw: String) = RingJson.decodeFromString<IncomingMessage>(raw)

    @Test
    fun `le son bypass-required est reconnu comme un refus`() {
        val passthru = message(
            """
            {
              "msg": "Passthru",
              "datatype": "PassthruType",
              "src": "asset-1",
              "body": [
                {
                  "zid": "panel-zid",
                  "type": "speaker-event",
                  "data": { "event": { "path": "localized/fr-fr/bypass-required.wav" } }
                }
              ]
            }
            """
        )

        assertTrue(announcesBypassRequired(passthru))
    }

    @Test
    fun `la langue du son ne change pas la detection`() {
        val passthru = message(
            """
            {
              "msg": "Passthru",
              "body": [
                { "type": "speaker-event", "data": { "event": { "path": "localized/de-de/bypass-required.wav" } } }
              ]
            }
            """
        )

        assertTrue(announcesBypassRequired(passthru))
    }

    @Test
    fun `un autre son ne declenche pas de bypass`() {
        val passthru = message(
            """
            {
              "msg": "Passthru",
              "body": [
                { "type": "speaker-event", "data": { "event": { "path": "localized/fr-fr/entry-delay.wav" } } }
              ]
            }
            """
        )

        assertFalse(announcesBypassRequired(passthru))
    }

    @Test
    fun `un document d appareil n est jamais un bypass`() {
        val document = message(
            """
            { "msg": "DeviceInfoDocGetList", "datatype": "DeviceInfoDocType", "body": [] }
            """
        )

        assertFalse(announcesBypassRequired(document))
    }

    @Test
    fun `l etat faulted est lu sur le document appareil`() {
        val flattened = flattenDeviceDoc(
            RingJson.decodeFromString(
                """
                {
                  "general": { "v2": { "zid": "z1", "deviceType": "sensor.contact", "name": "Fenetre living" } },
                  "device": { "v1": { "faulted": true } }
                }
                """
            )
        )

        val device = flattened.toPanelDevice(assetUuid = "asset-1")

        requireNotNull(device)
        assertTrue(device.faulted)
        assertTrue(device.isFaultedSensor)
    }

    @Test
    fun `un capteur au repos n est pas en defaut`() {
        val flattened = flattenDeviceDoc(
            RingJson.decodeFromString(
                """
                {
                  "general": { "v2": { "zid": "z2", "deviceType": "sensor.contact", "name": "Porte" } },
                  "device": { "v1": { "faulted": false } }
                }
                """
            )
        )

        val device = flattened.toPanelDevice(assetUuid = "asset-1")

        requireNotNull(device)
        assertFalse(device.faulted)
        assertFalse(device.isFaultedSensor)
    }

    @Test
    fun `seuls les capteurs sont retenus comme en defaut`() {
        val devices = listOf(
            PanelDevice("z1", "a", "sensor.contact", "Fenetre living", null, null, faulted = true),
            PanelDevice("z2", "a", "sensor.motion", "Detecteur cuisine", null, null, faulted = true),
            PanelDevice("z3", "a", "sensor.contact", "Porte", null, null, faulted = false),
            PanelDevice("z4", "a", "adapter.zigbee", "Adaptateur", null, null, faulted = true),
        )

        val faulted = devices.faultedSensors()

        assertEquals(
            listOf(
                FaultedSensor("z1", "Fenetre living"),
                FaultedSensor("z2", "Detecteur cuisine"),
            ),
            faulted,
        )
    }
}
