package com.riz.app.crypto

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.util.Arrays
import java.util.concurrent.ConcurrentLinkedQueue
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private const val ZERO_CHAR: Char = Char.MIN_VALUE
private const val ZERO_BYTE: Byte = 0

/**
 * Holds a Memory-only precomputed salt + derived master key pair for a single encryption op.
 */
data class DerivedKeyEntry(
    val salt: ByteArray,
    val masterKey: SecretKeySpec,
) {
    override fun toString(): String = "DerivedKeyEntry(salt=[REDACTED], masterKey=[REDACTED])"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DerivedKeyEntry) return false
        return salt.contentEquals(other.salt)
    }

    override fun hashCode(): Int = salt.contentHashCode()
}

/**
 * In-memory pool of precomputed [DerivedKeyEntry] items.
 */
class DerivedKeyPool(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    companion object {
        const val TARGET_SIZE = 12
        private const val SALT_SIZE = 16
        private const val ITERATIONS = 600000
        private const val KEY_LENGTH_BIT = 256
    }

    private val pool = ConcurrentLinkedQueue<DerivedKeyEntry>()
    private val refillMutex = Mutex()
    private var refillJob: Job? = null
    private var activePassword: String? = null
    private val passwordMutex = Mutex()

    fun initialize(password: String) {
        scope.launch {
            passwordMutex.withLock {
                if (activePassword == password && pool.isNotEmpty()) return@launch
                activePassword = password
            }
            scheduleRefill()
        }
    }

    suspend fun acquireKey(password: String): DerivedKeyEntry {
        val entry = pool.poll()
        if (entry != null) {
            scheduleRefill()
            return entry
        }
        return generateEntry(password)
    }

    suspend fun invalidate() {
        refillJob?.cancel()
        refillJob = null

        passwordMutex.withLock {
            activePassword = null
        }

        while (true) {
            val entry = pool.poll() ?: break
            zeroEntry(entry)
        }
    }

    private fun scheduleRefill() {
        refillJob =
            scope.launch {
                refillMutex.withLock {
                    val currentPassword =
                        passwordMutex.withLock { activePassword }
                            ?: return@launch

                    val needed = TARGET_SIZE - pool.size
                    if (needed <= 0) return@launch

                    repeat(needed) {
                        ensureActive()
                        val entry = generateEntry(currentPassword)
                        pool.offer(entry)
                    }
                }
            }
    }

    private suspend fun generateEntry(password: String): DerivedKeyEntry =
        withContext(dispatcher) {
            val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val passChars = password.toCharArray()
            try {
                val spec = PBEKeySpec(passChars, salt, ITERATIONS, KEY_LENGTH_BIT)
                try {
                    val keyBytes = factory.generateSecret(spec).encoded
                    DerivedKeyEntry(
                        salt = salt,
                        masterKey = SecretKeySpec(keyBytes, "AES"),
                    ).also {
                        Arrays.fill(keyBytes, ZERO_BYTE)
                    }
                } finally {
                    spec.clearPassword()
                }
            } finally {
                Arrays.fill(passChars, ZERO_CHAR)
            }
        }

    private fun zeroEntry(entry: DerivedKeyEntry) {
        try {
            Arrays.fill(entry.salt, ZERO_BYTE)
            val encoded = entry.masterKey.encoded
            Arrays.fill(encoded, ZERO_BYTE)
        } catch (_: Exception) {
            // Best-effort
        }
    }
}
