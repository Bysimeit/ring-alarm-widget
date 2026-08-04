package dev.ringalarmwidget.core.panel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ModeChangeTest {

    private fun panel(mode: AlarmMode?, transitioning: Boolean = false) = PanelDevice(
        zid = "panel-zid",
        assetUuid = "asset-1",
        deviceType = PanelDevice.SECURITY_PANEL_TYPE,
        name = "Panneau",
        mode = mode,
        transitionDelayEndTimestamp = if (transitioning) 1_754_000_000_000 else null,
    )

    @Test
    fun `mise a jour conforme au mode demande`() {
        val watch = PanelWatch(matched = panel(AlarmMode.AWAY), lastSeen = panel(AlarmMode.AWAY))

        val result = interpretModeChange(AlarmMode.AWAY, watch)

        val ready = assertIs<PanelResult.Ready>(result)
        assertEquals(AlarmMode.AWAY, ready.snapshot.mode)
    }

    @Test
    fun `mise a jour divergente signale un refus avec le mode observe`() {
        val watch = PanelWatch(matched = null, lastSeen = panel(AlarmMode.DISARMED))

        val result = interpretModeChange(AlarmMode.AWAY, watch)

        assertEquals(PanelResult.Rejected(AlarmMode.AWAY, AlarmMode.DISARMED), result)
    }

    @Test
    fun `silence complet du panneau est un delai depasse et non un refus`() {
        val watch = PanelWatch(matched = null, lastSeen = null)

        val result = interpretModeChange(AlarmMode.AWAY, watch)

        assertEquals(PanelResult.TimedOut(STAGE_MODE_CONFIRMATION), result)
    }

    @Test
    fun `le delai de sortie est repercute dans l instantane`() {
        val watch = PanelWatch(
            matched = panel(AlarmMode.AWAY, transitioning = true),
            lastSeen = null,
        )

        val ready = assertIs<PanelResult.Ready>(interpretModeChange(AlarmMode.AWAY, watch))

        assertEquals(true, ready.snapshot.transitioning)
    }
}
