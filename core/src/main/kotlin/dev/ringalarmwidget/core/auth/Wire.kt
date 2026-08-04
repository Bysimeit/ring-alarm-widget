package dev.ringalarmwidget.core.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class PasswordGrantBody(
    @SerialName("client_id") val clientId: String,
    @SerialName("grant_type") val grantType: String = "password",
    val username: String,
    val password: String,
    val scope: String = "client",
)

@Serializable
internal data class RefreshGrantBody(
    @SerialName("client_id") val clientId: String,
    @SerialName("grant_type") val grantType: String = "refresh_token",
    @SerialName("refresh_token") val refreshToken: String,
    val scope: String = "client",
)

@Serializable
internal data class TokenPayload(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long = 3600,
)

@Serializable
internal data class ChallengePayload(
    @SerialName("tsv_state") val tsvState: String? = null,
    @SerialName("phone") val phone: String? = null,
    @SerialName("next_time_in_secs") val nextTimeInSecs: Long? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)
