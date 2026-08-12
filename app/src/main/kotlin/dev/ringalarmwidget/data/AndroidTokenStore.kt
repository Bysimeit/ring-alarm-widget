package dev.ringalarmwidget.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.ringalarmwidget.core.auth.Session
import dev.ringalarmwidget.core.auth.TokenStore
import dev.ringalarmwidget.core.net.RingJson
import dev.ringalarmwidget.core.panel.AlarmMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.preferences: DataStore<Preferences> by preferencesDataStore(name = "ring-alarm-widget")

class AndroidTokenStore(context: Context) : TokenStore {

    private val store = context.applicationContext.preferences

    override suspend fun load(): Session? {
        val payload = store.data.first()[KEY_SESSION] ?: return null
        val plain = decrypt(payload) ?: return null
        return runCatching { RingJson.decodeFromString<Session>(plain) }.getOrNull()
    }

    override suspend fun save(session: Session) {
        val payload = encrypt(RingJson.encodeToString(session))
        store.edit { it[KEY_SESSION] = payload }
    }

    override suspend fun clear() {
        store.edit {
            it.remove(KEY_SESSION)
            it.remove(KEY_LOCATION_ID)
            it.remove(KEY_LOCATION_NAME)
            it.remove(KEY_MODE)
            it.remove(KEY_PENDING)
            it.remove(KEY_FAILED)
            it.remove(KEY_TRANSITION)
            it.remove(KEY_BYPASS)
            it.remove(KEY_REFRESHING)
            it.remove(KEY_READ_AT)
        }
    }

    suspend fun hardwareId(): String {
        store.data.first()[KEY_HARDWARE_ID]?.let { return it }

        val generated = UUID.randomUUID().toString()
        var stored = generated
        store.edit { preferences ->
            val existing = preferences[KEY_HARDWARE_ID]
            if (existing == null) {
                preferences[KEY_HARDWARE_ID] = generated
            } else {
                stored = existing
            }
        }
        return stored
    }

    suspend fun panelCache(): PanelCache? {
        val preferences = store.data.first()
        val locationId = preferences[KEY_LOCATION_ID] ?: return null
        return PanelCache(
            locationId = locationId,
            locationName = preferences[KEY_LOCATION_NAME],
            mode = AlarmMode.fromWire(preferences[KEY_MODE]?.let { decrypt(it) }),
        )
    }

    suspend fun setLocation(locationId: String, name: String) {
        store.edit {
            it[KEY_LOCATION_ID] = locationId
            it[KEY_LOCATION_NAME] = name
        }
    }

    suspend fun setCachedMode(mode: AlarmMode?) {
        store.edit {
            if (mode == null) it.remove(KEY_MODE) else it[KEY_MODE] = encrypt(mode.wireValue)
        }
    }

    suspend fun setPendingMode(mode: AlarmMode?) {
        store.edit {
            if (mode == null) {
                it.remove(KEY_PENDING)
            } else {
                it[KEY_PENDING] = encrypt("${mode.wireValue}$SEPARATOR${System.currentTimeMillis()}")
            }
        }
    }

    suspend fun setLastAttemptFailed(failed: Boolean) {
        store.edit { if (failed) it[KEY_FAILED] = true else it.remove(KEY_FAILED) }
    }

    suspend fun setRefreshing(refreshing: Boolean) {
        if (!refreshing && store.data.first()[KEY_REFRESHING] == null) return
        store.edit {
            if (refreshing) {
                it[KEY_REFRESHING] = System.currentTimeMillis()
            } else {
                it.remove(KEY_REFRESHING)
            }
        }
    }

    suspend fun setReadAt(readAt: Long) {
        store.edit { it[KEY_READ_AT] = readAt }
    }

    suspend fun readAt(): Long? = store.data.first()[KEY_READ_AT]

    suspend fun bypassPrompt(): BypassPrompt? = livePrompt(store.data.first()[KEY_BYPASS])

    suspend fun setBypassPrompt(prompt: BypassPrompt?) {
        store.edit {
            if (prompt == null) {
                it.remove(KEY_BYPASS)
            } else {
                it[KEY_BYPASS] = encrypt(RingJson.encodeToString(prompt))
            }
        }
    }

    suspend fun confirmDisarm(): Boolean = store.data.first()[KEY_CONFIRM_DISARM] ?: false

    suspend fun setConfirmDisarm(enabled: Boolean) {
        store.edit { it[KEY_CONFIRM_DISARM] = enabled }
    }

    suspend fun themeChoice(): ThemeChoice =
        ThemeChoice.fromStored(store.data.first()[KEY_THEME])

    suspend fun setThemeChoice(choice: ThemeChoice) {
        store.edit { it[KEY_THEME] = choice.name }
    }

    fun widgetSnapshots(): Flow<WidgetSnapshot> = store.data.map { preferences ->
        val mode = AlarmMode.fromWire(preferences[KEY_MODE]?.let { decrypt(it) })
        val readAt = preferences[KEY_READ_AT]

        WidgetSnapshot(
            signedIn = preferences[KEY_SESSION] != null,
            mode = mode,
            pending = livePendingMode(preferences[KEY_PENDING]),
            refreshing = liveRefreshing(preferences[KEY_REFRESHING]),
            failed = preferences[KEY_FAILED] ?: false,
            stale = mode != null && System.currentTimeMillis() - (readAt ?: 0) > STALE_MILLIS,
            theme = ThemeChoice.fromStored(preferences[KEY_WIDGET_THEME]),
            transitionEndsAt = preferences[KEY_TRANSITION]?.takeIf { it > System.currentTimeMillis() },
            bypass = livePrompt(preferences[KEY_BYPASS]),
        )
    }

    suspend fun setTransitionEndsAt(endsAt: Long?) {
        store.edit {
            if (endsAt == null) it.remove(KEY_TRANSITION) else it[KEY_TRANSITION] = endsAt
        }
    }

    private fun livePrompt(payload: String?): BypassPrompt? {
        val plain = payload?.let { decrypt(it) } ?: return null
        val prompt = runCatching { RingJson.decodeFromString<BypassPrompt>(plain) }.getOrNull() ?: return null
        if (System.currentTimeMillis() - prompt.requestedAtEpochMillis > BYPASS_TTL_MILLIS) return null
        return prompt.takeIf { it.requested != null }
    }

    private fun liveRefreshing(startedAt: Long?): Boolean =
        startedAt != null && System.currentTimeMillis() - startedAt <= REFRESHING_TTL_MILLIS

    private fun livePendingMode(payload: String?): AlarmMode? {
        val raw = payload?.let { decrypt(it) } ?: return null
        val requestedAt = raw.substringAfter(SEPARATOR, "").toLongOrNull() ?: return null
        if (System.currentTimeMillis() - requestedAt > PENDING_TTL_MILLIS) return null
        return AlarmMode.fromWire(raw.substringBefore(SEPARATOR))
    }

    suspend fun widgetThemeChoice(): ThemeChoice =
        ThemeChoice.fromStored(store.data.first()[KEY_WIDGET_THEME])

    suspend fun setWidgetThemeChoice(choice: ThemeChoice) {
        store.edit { it[KEY_WIDGET_THEME] = choice.name }
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val combined = cipher.iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(payload: String): String? = runCatching {
        val combined = Base64.decode(payload, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }.getOrNull()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        val KEY_SESSION = stringPreferencesKey("session")
        val KEY_HARDWARE_ID = stringPreferencesKey("hardware_id")
        val KEY_LOCATION_ID = stringPreferencesKey("location_id")
        val KEY_LOCATION_NAME = stringPreferencesKey("location_name")
        val KEY_MODE = stringPreferencesKey("panel_mode")
        val KEY_PENDING = stringPreferencesKey("pending_mode")
        val KEY_FAILED = booleanPreferencesKey("last_attempt_failed")
        val KEY_CONFIRM_DISARM = booleanPreferencesKey("confirm_disarm")
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_WIDGET_THEME = stringPreferencesKey("widget_theme")
        val KEY_TRANSITION = longPreferencesKey("transition_ends_at")
        val KEY_BYPASS = stringPreferencesKey("bypass_prompt")
        val KEY_REFRESHING = longPreferencesKey("refreshing_since")
        val KEY_READ_AT = longPreferencesKey("read_at")

        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "session-key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_BITS = 128
        const val SEPARATOR = "@"
        const val PENDING_TTL_MILLIS = 150_000L
        const val BYPASS_TTL_MILLIS = 300_000L
        const val REFRESHING_TTL_MILLIS = 90_000L
        const val STALE_MILLIS = 2_700_000L
    }
}
