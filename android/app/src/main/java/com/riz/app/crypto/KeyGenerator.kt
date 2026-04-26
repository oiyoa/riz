package com.riz.app.crypto

import java.security.SecureRandom

/**
 * Utility for generating cryptographically secure keys.
 */
object KeyGenerator {
    private val secureRandom = SecureRandom()

    /**
     * Generates a 32-character hexadecimal string from 16 random bytes.
     * Provides 128 bits of entropy.
     */
    fun generateSecureKey(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
