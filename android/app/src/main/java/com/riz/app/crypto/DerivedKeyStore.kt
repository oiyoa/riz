package com.riz.app.crypto

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import java.security.KeyStore
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Securely persists precomputed derived keys using the Android Keystore.
 *
 * Each derived key (raw AES-256 bytes) is wrapped with AES-GCM using a
 * hardware-backed Keystore key before being written to SharedPreferences.
 * This ensures key material is never stored in plaintext on disk.
 *
 * Uses a separate Keystore alias from password storage for isolation.
 */
class DerivedKeyStore(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "DerivedKeyStore"
        private const val PREFS_NAME = "riz_key_pool_prefs"
        private const val KEY_ALIAS = "RizDerivedKeyPoolKey"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val COUNT_KEY = "pool_count"

        // Per-entry key prefixes
        private const val SALT_PREFIX = "pool_salt_"
        private const val KEY_PREFIX = "pool_key_"
        private const val IV_PREFIX = "pool_iv_"

        private const val GCM_TAG_LENGTH = 128
    }

    /**
     * Returns or creates the Android Keystore wrapping key for pool entries.
     */
    private fun getOrCreateWrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator =
            KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE,
            )
        val spec =
            KeyGenParameterSpec
                .Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Wraps raw key bytes with the Keystore key. Returns (ciphertext, iv) as Base64 strings.
     */
    private fun wrapKeyBytes(rawKey: ByteArray): Pair<String, String>? =
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(rawKey)
            Pair(
                Base64.encodeToString(encrypted, Base64.NO_WRAP),
                Base64.encodeToString(iv, Base64.NO_WRAP),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to wrap key bytes", e)
            null
        }

    /**
     * Unwraps key bytes using the Keystore key.
     */
    private fun unwrapKeyBytes(
        encBase64: String,
        ivBase64: String,
    ): ByteArray? =
        try {
            val enc = Base64.decode(encBase64, Base64.NO_WRAP)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateWrappingKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
            cipher.doFinal(enc)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unwrap key bytes", e)
            null
        }

    /**
     * Loads all persisted pool entries. Invalid entries are silently skipped.
     */
    fun loadAll(): List<DerivedKeyEntry> {
        val count = prefs.getInt(COUNT_KEY, 0)
        if (count == 0) return emptyList()

        val entries = mutableListOf<DerivedKeyEntry>()
        for (i in 0 until count) {
            loadEntry(i)?.let { entries.add(it) }
        }
        return entries
    }

    private fun loadEntry(index: Int): DerivedKeyEntry? {
        val saltBase64 = prefs.getString("$SALT_PREFIX$index", null)
        val keyBase64 = prefs.getString("$KEY_PREFIX$index", null)
        val ivBase64 = prefs.getString("$IV_PREFIX$index", null)

        return if (saltBase64 != null && keyBase64 != null && ivBase64 != null) {
            val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
            val rawKey = unwrapKeyBytes(keyBase64, ivBase64)
            if (rawKey != null) {
                val e = DerivedKeyEntry(salt = salt, masterKey = SecretKeySpec(rawKey, "AES"))
                Arrays.fill(rawKey, 0.toByte())
                e
            } else {
                null
            }
        } else {
            null
        }
    }

    /**
     * Persists all pool entries atomically, replacing any previous data.
     */
    fun saveAll(entries: List<DerivedKeyEntry>) {
        prefs.edit {
            // Clear previous entries
            val oldCount = prefs.getInt(COUNT_KEY, 0)
            for (i in 0 until oldCount) {
                remove("$SALT_PREFIX$i")
                remove("$KEY_PREFIX$i")
                remove("$IV_PREFIX$i")
            }

            // Write new entries
            putInt(COUNT_KEY, entries.size)
            entries.forEachIndexed { index, entry ->
                val saltBase64 =
                    Base64.encodeToString(
                        entry.salt,
                        Base64.NO_WRAP,
                    )
                val wrapped = wrapKeyBytes(entry.masterKey.encoded) ?: return@forEachIndexed

                putString("$SALT_PREFIX$index", saltBase64)
                putString("$KEY_PREFIX$index", wrapped.first)
                putString("$IV_PREFIX$index", wrapped.second)
            }
        }
    }

    /**
     * Removes all persisted pool entries.
     */
    fun clear() {
        prefs.edit {
            val count = prefs.getInt(COUNT_KEY, 0)
            for (i in 0 until count) {
                remove("$SALT_PREFIX$i")
                remove("$KEY_PREFIX$i")
                remove("$IV_PREFIX$i")
            }
            remove(COUNT_KEY)
        }
    }
}
