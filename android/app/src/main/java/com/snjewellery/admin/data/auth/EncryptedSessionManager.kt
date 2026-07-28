package com.snjewellery.admin.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the admin's session, encrypted with a key held in the Android
 * Keystore.
 *
 * ── Why not the SDK's default ────────────────────────────────────────
 * supabase-kt's `SettingsSessionManager` writes the session to plain
 * SharedPreferences. The app sandbox already keeps that from other apps,
 * and `allowBackup=false` plus the data-extraction rules keep it off
 * cloud backups — but the value stored is a **refresh token for an admin
 * account**, which is long-lived and grants write access to the whole
 * catalogue through RLS. It is the most sensitive thing this app holds,
 * and leaving it in cleartext on disk is a worse default than it costs to
 * fix. See CLAUDE.md §9.
 *
 * Jetpack Security's `EncryptedSharedPreferences` was the obvious route
 * and is deprecated, so this uses the Keystore directly: AES-256/GCM,
 * key non-exportable, generated once on first save.
 *
 * ── Failure degrades to a re-login, never to a crash ─────────────────
 * Any problem reading the stored session — a rotated key after a restore
 * to a new device, a corrupt value, a format change in a future version —
 * returns `null`, which the SDK treats as "no session". The owner signs in
 * again. That is the important property: hand-rolled storage is exactly
 * where a subtle bug would otherwise brick the app for someone with a
 * shop to run, and a re-login is a cost worth paying to rule that out.
 */
@Singleton
class EncryptedSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : SessionManager {

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun saveSession(session: UserSession) {
        try {
            val plaintext = json.encodeToString(UserSession.serializer(), session)
            prefs.edit { putString(KEY_SESSION, encrypt(plaintext)) }
        } catch (e: GeneralSecurityException) {
            // Not fatal. The session stays in memory, so the current
            // login keeps working and only survival across a restart is
            // lost — better than failing a sign-in that has succeeded.
            clear()
        }
    }

    override suspend fun loadSession(): UserSession? {
        val stored = prefs.getString(KEY_SESSION, null) ?: return null
        return try {
            json.decodeFromString(UserSession.serializer(), decrypt(stored))
        } catch (e: Exception) {
            // Deliberately broad: a decryption failure, a truncated
            // value, and a schema change all mean the same thing here —
            // there is no usable session. Drop it so the next save starts
            // clean rather than reading the same bad value forever.
            clear()
            null
        }
    }

    override suspend fun deleteSession() = clear()

    private fun clear() {
        prefs.edit { remove(KEY_SESSION) }
    }

    // ── Crypto ───────────────────────────────────────────────────────
    // The IV is generated per encryption by the cipher and stored with the
    // ciphertext, because GCM is catastrophically broken by IV reuse and
    // letting the provider choose removes the chance of getting it wrong.

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray())
        return "${base64(cipher.iv)}$SEPARATOR${base64(ciphertext)}"
    }

    private fun decrypt(stored: String): String {
        val (iv, ciphertext) = stored.split(SEPARATOR, limit = 2)
            .also { require(it.size == 2) { "stored session is not iv$SEPARATOR" + "ciphertext" } }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(GCM_TAG_BITS, unBase64(iv)),
        )
        return String(cipher.doFinal(unBase64(ciphertext)))
    }

    /** The existing key, or a new one on first use. Never leaves the Keystore. */
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)
            ?.let { return it.secretKey }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    // Deliberately NOT setUserAuthenticationRequired: the
                    // owner photographs stock between customers, and a
                    // device-credential prompt on every launch is the kind
                    // of friction that gets an app abandoned. The device
                    // lock screen is the boundary here, not the app.
                    .build(),
            )
        }.generateKey()
    }

    private fun base64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unBase64(value: String) = Base64.decode(value, Base64.NO_WRAP)

    private companion object {
        const val PREFS_NAME = "sn_session"
        const val KEY_SESSION = "session"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "sn_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val GCM_TAG_BITS = 128

        /** Not valid base64, so it cannot occur inside either field. */
        const val SEPARATOR = ":"
    }
}
