package dev.ringalarmwidget.core.auth

import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
) {
    fun needsRefresh(nowEpochSeconds: Long, marginSeconds: Long = REFRESH_MARGIN_SECONDS): Boolean =
        nowEpochSeconds >= expiresAtEpochSeconds - marginSeconds

    companion object {
        const val REFRESH_MARGIN_SECONDS: Long = 120
    }
}
