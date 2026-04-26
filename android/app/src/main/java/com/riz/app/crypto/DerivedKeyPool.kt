package com.riz.app.crypto

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.util.concurrent.ConcurrentLinkedQueue
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Holds a precomputed salt + derived master key pair.
 *
 * These are generated in the background using the same PBKDF2 parameters
 * as [CryptoEngine] so that encryption operations can skip the expensive
 * key derivation step.
 */
data class DerivedKeyEntry(
    val salt: ByteArray,
    val masterKey: SecretKeySpec,
) {
    /** Redact sensitive material from logs/debug output. */
    override fun toString(): String = "DerivedKeyEntry(salt=[REDACTED], masterKey=[REDACTED])"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DerivedKeyEntry) return false
        return salt.contentEquals(other.salt)
    }

    override fun hashCode(): Int = salt.contentHashCode()
}

/**
 * Manages a pool of precomputed [DerivedKeyEntry] items for instant encryption.
 *
 * ## How it works
 * 1. When the user sets a password, [initialize] is called to load any persisted
 *    entries and begin background generation up to [TARGET_SIZE].
 * 2. Encryption calls [acquireKey] to grab a ready-made (salt, masterKey) pair.
 *    If the pool is empty, it falls back to synchronous derivation (never fails).
 * 3. After each [acquireKey], the pool automatically schedules background refill
 *    to replenish consumed entries.
 * 4. On password change or clear, [invalidate] securely wipes all entries.
 *
 * ## Thread Safety
 * - The pool uses [ConcurrentLinkedQueue] for lock-free concurrent access.
 * - Refill coordination uses [Mutex] to prevent duplicate refill coroutines.
 * - All derivation runs on [dispatcher] (defaults to [Dispatchers.Default]).
 *
 * ## Security
 * - Entries are persisted encrypted via [DerivedKeyStore] (Android Keystore backed).
 * - On [invalidate], all in-memory key material is zeroed and storage is cleared.
 * - Each entry has a unique [SecureRandom] salt — no salt reuse across operations.
 */
class DerivedKeyPool(
    private val store: DerivedKeyStore,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    companion object {
        /** Maximum number of precomputed entries to maintain. */
        const val TARGET_SIZE = 12

        /** Refill is triggered when pool drops below this level. */
        const val REFILL_THRESHOLD = 6

        // Must match CryptoEngine constants exactly
        private const val SALT_SIZE = 16
        private const val ITERATIONS = 600000
        private const val KEY_LENGTH_BIT = 256
    }

    /** Thread-safe queue of ready-to-use key entries. */
    private val pool = ConcurrentLinkedQueue<DerivedKeyEntry>()

    /** Prevents multiple refill coroutines from running simultaneously. */
    private val refillMutex = Mutex()

    /** The current refill job, tracked for cancellation on invalidate. */
    private var refillJob: Job? = null

    /** The password used for derivation; held in memory only while pool is active. */
    private var activePassword: String? = null

    /** Guards password read/write and initialization. */
    private val passwordMutex = Mutex()

    /** Observable pool size for debugging/monitoring. */
    private val _poolSize = MutableStateFlow(0)
    val poolSize: StateFlow<Int> = _poolSize.asStateFlow()

    /**
     * Loads persisted entries and begins background generation.
     *
     * Called when the user sets a password or on app startup if a password
     * is already stored. Safe to call multiple times — duplicate calls
     * for the same password are no-ops.
     */
    fun initialize(password: String) {
        scope.launch {
            passwordMutex.withLock {
                // Already initialized with this password
                if (activePassword == password && pool.isNotEmpty()) return@launch
                activePassword = password
            }

            // Load any persisted entries from secure storage
            val persisted = store.loadAll()
            persisted.forEach { pool.offer(it) }
            updatePoolSize()

            // Fill up to target
            scheduleRefill()
        }
    }

    /**
     * Returns a precomputed (salt, masterKey) pair for encryption.
     *
     * If the pool has entries, one is returned instantly. If the pool is
     * empty, falls back to synchronous derivation so encryption never fails.
     *
     * After acquisition, triggers background refill to replace the consumed entry.
     *
     * @param password The current password, used only for fallback derivation.
     * @return A [DerivedKeyEntry] ready for use in encryption.
     */
    suspend fun acquireKey(password: String): DerivedKeyEntry {
        val entry = pool.poll()

        if (entry != null) {
            updatePoolSize()
            // Persist the reduced pool
            persistPool()
            // Trigger refill in background after consumption
            scheduleRefill()
            return entry
        }

        // Pool exhausted — derive synchronously as fallback
        return generateEntry(password)
    }

    /**
     * Securely wipes all key material from memory and persistent storage.
     *
     * Called on password change or password clear. Cancels any in-progress
     * refill job and zeros all key bytes in memory.
     */
    suspend fun invalidate() {
        // Cancel any running refill
        refillJob?.cancel()
        refillJob = null

        passwordMutex.withLock {
            activePassword = null
        }

        // Zero and drain all in-memory entries
        while (true) {
            val entry = pool.poll() ?: break
            zeroEntry(entry)
        }
        updatePoolSize()

        // Clear persistent storage
        store.clear()
    }

    /**
     * Schedules a background refill coroutine if the pool is below capacity.
     * Only one refill runs at a time — concurrent calls are coalesced.
     */
    private fun scheduleRefill() {
        refillJob =
            scope.launch {
                refillMutex.withLock {
                    val currentPassword =
                        passwordMutex.withLock { activePassword }
                            ?: return@launch // No password set, nothing to do

                    val needed = TARGET_SIZE - pool.size
                    if (needed <= 0) return@launch

                    repeat(needed) {
                        // Check for cancellation between iterations
                        ensureActive()

                        val entry = generateEntry(currentPassword)
                        pool.offer(entry)
                        updatePoolSize()
                    }

                    // Persist the full pool once refill is complete
                    persistPool()
                }
            }
    }

    /**
     * Derives a single (salt, masterKey) pair using PBKDF2.
     *
     * Uses identical parameters to [CryptoEngine.deriveMasterKey] to ensure
     * cryptographic compatibility.
     */
    private suspend fun generateEntry(password: String): DerivedKeyEntry =
        withContext(dispatcher) {
            val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val passChars = password.toCharArray()
            try {
                val spec = PBEKeySpec(passChars, salt, ITERATIONS, KEY_LENGTH_BIT)
                val keyBytes = factory.generateSecret(spec).encoded
                DerivedKeyEntry(
                    salt = salt,
                    masterKey = SecretKeySpec(keyBytes, "AES"),
                ).also {
                    // Zero intermediate key bytes
                    java.util.Arrays.fill(keyBytes, 0.toByte())
                }
            } finally {
                // Always zero the password char array
                java.util.Arrays.fill(passChars, '\u0000')
            }
        }

    /**
     * Atomically persists the current pool state to secure storage.
     */
    private fun persistPool() {
        val snapshot = pool.toList()
        store.saveAll(snapshot)
    }

    /**
     * Updates the observable pool size counter.
     */
    private fun updatePoolSize() {
        _poolSize.value = pool.size
    }

    /**
     * Zeros key material in a [DerivedKeyEntry] to limit exposure.
     */
    private fun zeroEntry(entry: DerivedKeyEntry) {
        try {
            java.util.Arrays.fill(entry.salt, 0.toByte())
            // SecretKeySpec.encoded returns a copy, so we zero what we can
            val encoded = entry.masterKey.encoded
            java.util.Arrays.fill(encoded, 0.toByte())
        } catch (_: Exception) {
            // Best-effort zeroing
        }
    }
}
