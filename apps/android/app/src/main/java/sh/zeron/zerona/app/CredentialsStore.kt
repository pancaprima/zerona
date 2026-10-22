package sh.zeron.zerona.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import uniffi.zeron_core.AuthTokens

data class StoredCredentials(
    val userId: String,
    val orgId: String,
    val accessToken: String,
    val refreshToken: String,
)

class CredentialsStore(
    context: Context,
) {
    private val prefs =
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    fun stored(): StoredCredentials? {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val orgId = prefs.getString(KEY_ORG_ID, null) ?: return null
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        return StoredCredentials(userId, orgId, access, refresh)
    }

    fun store(
        userId: String,
        orgId: String,
        tokens: AuthTokens,
    ) {
        prefs
            .edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_ORG_ID, orgId)
            .putString(KEY_ACCESS, tokens.accessToken)
            .putString(KEY_REFRESH, tokens.refreshToken)
            .apply()
    }

    fun updateTokens(tokens: AuthTokens) {
        val existing = stored() ?: return
        store(existing.userId, existing.orgId, tokens)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "zerona.credentials"
        private const val KEY_USER_ID = "userId"
        private const val KEY_ORG_ID = "orgId"
        private const val KEY_ACCESS = "access"
        private const val KEY_REFRESH = "refresh"
    }
}
