package com.riz.app.data.repository

import com.riz.app.crypto.CryptoEngine
import com.riz.app.crypto.DerivedKeyPool
import com.riz.app.crypto.FileEntry
import com.riz.app.data.PasswordStore

/**
 * Mediates between the UI/ViewModel layer and the crypto/storage layers.
 *
 * Manages the [DerivedKeyPool] lifecycle in response to password changes:
 * - On [setPassword]: invalidates old pool, initializes new one for background precomputation.
 * - On [clearPassword]: wipes all precomputed keys.
 * - On [initializePool]: loads persisted keys on app startup if a password is already set.
 *
 * All encrypt methods pass the key pool through to [CryptoEngine] for instant key acquisition.
 * Decrypt methods are unaffected — they derive keys from the ciphertext's embedded salt.
 */
class SecurityRepository(
    private val passwordStore: PasswordStore,
    private val keyPool: DerivedKeyPool,
) {
    fun getPassword(): String? = passwordStore.getPassword()

    fun setPassword(password: String): Boolean {
        val changed = passwordStore.setPassword(password)
        if (changed) {
            // Wipe old keys and start generating new ones for the new password
            keyPool.initialize(password)
        }
        return changed
    }

    fun clearPassword() {
        passwordStore.clearPassword()
        // Wipe all precomputed keys from memory and storage
        kotlinx.coroutines.runBlocking {
            keyPool.invalidate()
        }
    }

    /**
     * Initializes the key pool on app startup if a password is already stored.
     * Should be called once from [com.riz.app.RizApplication.onCreate].
     */
    fun initializePool() {
        val pwd = passwordStore.getPassword()
        if (pwd != null) {
            keyPool.initialize(pwd)
        }
    }

    suspend fun encryptSingleFile(
        name: String,
        data: ByteArray,
        password: String,
    ): ByteArray = CryptoEngine.encryptSingleFile(name, data, password, keyPool)

    suspend fun encryptMultiFiles(
        files: List<FileEntry>,
        password: String,
    ): ByteArray = CryptoEngine.encryptMultiFiles(files, password, keyPool)

    suspend fun encryptMessage(
        text: String,
        password: String,
    ): ByteArray = CryptoEngine.encryptMessage(text, password, keyPool)

    suspend fun decryptBytes(
        bytes: ByteArray,
        password: String,
    ): CryptoEngine.DecryptResult = CryptoEngine.decryptBytes(bytes, password)
}
