package dev.ringalarmwidget.core.panel

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PanelResultTest {

    private fun snapshot() = PanelSnapshot(
        zid = "panel-zid",
        assetUuid = "asset-1",
        name = "Panneau",
        mode = AlarmMode.HOME,
        transitioning = false,
        transitionEndsAtEpochMillis = null,
    )

    @Test
    fun `un 401 est un rejet de jeton`() {
        assertTrue(PanelResult.Unreachable(401, "unauthorized").rejectsCredentials)
    }

    @Test
    fun `un 403 est un rejet de jeton`() {
        assertTrue(PanelResult.Unreachable(403, "forbidden").rejectsCredentials)
    }

    @Test
    fun `une panne reseau sans code n est pas un rejet de jeton`() {
        assertFalse(PanelResult.Unreachable(null, "timeout").rejectsCredentials)
    }

    @Test
    fun `un 500 n est pas un rejet de jeton`() {
        assertFalse(PanelResult.Unreachable(500, "server error").rejectsCredentials)
    }

    @Test
    fun `une coupure reseau merite une nouvelle tentative`() {
        assertTrue(PanelResult.Unreachable(null, "connection reset").isTransient)
        assertTrue(PanelResult.TimedOut(STAGE_MODE_CONFIRMATION).isTransient)
    }

    @Test
    fun `un refus du panneau ne merite pas de nouvelle tentative`() {
        assertFalse(PanelResult.Rejected(AlarmMode.AWAY, AlarmMode.DISARMED).isTransient)
        assertFalse(PanelResult.NoSecurityPanel.isTransient)
        assertFalse(PanelResult.NoUsableAsset.isTransient)
        assertFalse(PanelResult.Ready(snapshot()).isTransient)
    }

    @Test
    fun `un jeton rejete ne merite pas de nouvelle tentative reseau`() {
        assertFalse(PanelResult.Unreachable(401, "unauthorized").isTransient)
    }
}
