package com.riz.app.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class PasswordStore(
    context: Context?,
) {
    private val prefs: SharedPreferences? = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "PasswordStore"
        private const val PREFS_NAME = "riz_secure_prefs"
        private const val KEY_ALIAS = "RizAppPasswordKey"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val PWD_KEY = "encrypted_pwd"
        private const val IV_KEY = "encrypted_pwd_iv"
        private const val GCM_TAG_LENGTH = 128
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        val key = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (key != null) return key

        // Key missing or invalid, generate new one
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

    private fun wrapKey(key: ByteArray): Pair<String, String>? =
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val enc = cipher.doFinal(key)
            Pair(
                Base64.encodeToString(enc, Base64.DEFAULT),
                Base64.encodeToString(iv, Base64.DEFAULT),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to wrap key", e)
            null
        }

    private fun unwrapKey(
        encBase64: String,
        ivBase64: String,
    ): ByteArray? =
        try {
            val enc = Base64.decode(encBase64, Base64.DEFAULT)
            val iv = Base64.decode(ivBase64, Base64.DEFAULT)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
            cipher.doFinal(enc)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unwrap key", e)
            null
        }

    fun getPassword(): String? {
        val enc = prefs?.getString(PWD_KEY, null)
        val iv = prefs?.getString(IV_KEY, null)
        return if (enc != null && iv != null) unwrapKey(enc, iv)?.toString(Charsets.UTF_8) else null
    }

    fun setPassword(password: String): Boolean {
        if (password == getPassword()) return false // Same password, ignore

        val wrapped = wrapKey(password.toByteArray(Charsets.UTF_8))
        return if (wrapped != null) {
            prefs?.edit {
                putString(PWD_KEY, wrapped.first)
                putString(IV_KEY, wrapped.second)
            }
            true
        } else {
            false
        }
    }

    fun clearPassword() {
        prefs?.edit {
            remove(PWD_KEY)
            remove(IV_KEY)
        }
    }
}
