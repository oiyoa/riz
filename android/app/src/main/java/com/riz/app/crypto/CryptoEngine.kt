package com.riz.app.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoEngine {
    private const val SALT_SIZE = 16
    private const val NONCE_SIZE = 12
    private const val ITERATIONS = 600000
    private const val TAG_LENGTH_BIT = 128
    private const val KEY_LENGTH_BIT = 256
    private const val ALGORITHM = "AES/GCM/NoPadding"

    private const val VERSION: Byte = 0x01
    private const val HEADER_SIZE = 1
    private val HKDF_INFO = "riz/v1/message-key".toByteArray(Charsets.UTF_8)

    // Encryption Flags
    private const val FLAG_COMPRESSED: Byte = 0x01
    private const val FLAG_MULTI_FILE: Byte = 0x02

    private var masterKey: SecretKeySpec? = null
    private var masterPassword: String? = null
    private var salt: ByteArray? = null
    private val masterMutex = Mutex()

    private suspend fun deriveMasterKey(
        password: String,
        salt: ByteArray,
    ): SecretKeySpec =
        withContext(Dispatchers.Default) {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val passChars = password.toCharArray()
            try {
                val spec = PBEKeySpec(passChars, salt, ITERATIONS, KEY_LENGTH_BIT)
                val keyBytes = factory.generateSecret(spec).encoded
                SecretKeySpec(keyBytes, "AES").also {
                    java.util.Arrays.fill(keyBytes, 0.toByte())
                }
            } finally {
                java.util.Arrays.fill(passChars, '\u0000')
            }
        }

    private suspend fun getMasterKey(
        password: String,
        salt: ByteArray,
    ): SecretKeySpec =
        masterMutex.withLock {
            if (masterPassword == password && this.salt?.contentEquals(salt) == true) {
                masterKey!!
            } else {
                deriveMasterKey(password, salt).also {
                    masterPassword = password
                    this.salt = salt
                    masterKey = it
                }
            }
        }

    private fun deriveMessageKey(
        masterKey: SecretKeySpec,
        messageSalt: ByteArray,
    ): SecretKeySpec {
        val hmac = Mac.getInstance("HmacSHA256")
        // HKDF-Extract
        hmac.init(SecretKeySpec(messageSalt, "HmacSHA256"))
        val prk = hmac.doFinal(masterKey.encoded)
        // HKDF-Expand
        hmac.init(SecretKeySpec(prk, "HmacSHA256"))
        val keyBytes = hmac.doFinal(HKDF_INFO + 0x01.toByte())
        return SecretKeySpec(keyBytes, "AES")
    }

    private suspend fun encryptInternal(
        data: ByteArray,
        password: String,
        flags: Byte,
        keyPool: DerivedKeyPool? = null,
    ): ByteArray =
        withContext(Dispatchers.Default) {
            // Use precomputed key from pool if available, otherwise derive inline
            val (salt, mKey) =
                if (keyPool != null) {
                    val entry = keyPool.acquireKey(password)
                    entry.salt to entry.masterKey
                } else {
                    val s = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
                    s to getMasterKey(password, s)
                }

            val nonce = ByteArray(NONCE_SIZE).also { SecureRandom().nextBytes(it) }
            val msgKey = deriveMessageKey(mKey, nonce)

            val header = byteArrayOf(flags)
            val fullPlaintext = header + data

            val prefix = byteArrayOf(0x01) + nonce + salt

            val encrypted =
                Cipher.getInstance(ALGORITHM).run {
                    init(Cipher.ENCRYPT_MODE, msgKey, GCMParameterSpec(TAG_LENGTH_BIT, nonce))
                    updateAAD(prefix)
                    doFinal(fullPlaintext)
                }

            prefix + encrypted
        }

    suspend fun encryptSingleFile(
        name: String,
        data: ByteArray,
        password: String,
        keyPool: DerivedKeyPool? = null,
    ): ByteArray {
        val packed = BinaryFormat.packSingleFile(name, data)
        val compressed = Compression.compressData(packed)
        return encryptInternal(compressed, password, FLAG_COMPRESSED, keyPool)
    }

    suspend fun encryptMultiFiles(
        files: List<FileEntry>,
        password: String,
        keyPool: DerivedKeyPool? = null,
    ): ByteArray {
        val packed = BinaryFormat.packMultipleFiles(files)
        val compressed = Compression.compressData(packed)
        // Compressed | Multi-file
        val flags = (FLAG_COMPRESSED.toInt() or FLAG_MULTI_FILE.toInt()).toByte()
        return encryptInternal(compressed, password, flags, keyPool)
    }

    suspend fun encryptMessage(
        text: String,
        password: String,
        keyPool: DerivedKeyPool? = null,
    ): ByteArray {
        val packed = BinaryFormat.packSingleFile("", text.toByteArray(Charsets.UTF_8))
        val compressed = Compression.compressData(packed)
        return encryptInternal(compressed, password, FLAG_COMPRESSED, keyPool)
    }

    suspend fun decryptBytes(
        packed: ByteArray,
        password: String,
    ): DecryptResult =
        withContext(Dispatchers.Default) {
            val protoVer = packed[0].toInt() and 0xFF
            require(protoVer == 0x01) { "Invalid format. This data might not be from Riz." }

            val prefixLen = 1 + NONCE_SIZE + SALT_SIZE
            val minSize = prefixLen + HEADER_SIZE + (TAG_LENGTH_BIT / 8)
            require(packed.size >= minSize) { "Invalid ciphertext length" }

            val nonce = packed.copyOfRange(1, 1 + NONCE_SIZE)
            val salt = packed.copyOfRange(1 + NONCE_SIZE, prefixLen)

            val mKey = getMasterKey(password, salt)
            val msgKey = deriveMessageKey(mKey, nonce)

            val decryptedBuffer =
                Cipher.getInstance(ALGORITHM).run {
                    init(Cipher.DECRYPT_MODE, msgKey, GCMParameterSpec(TAG_LENGTH_BIT, nonce))
                    updateAAD(packed, 0, prefixLen)
                    doFinal(packed, prefixLen, packed.size - prefixLen)
                }

            val dec = Compression.decompressData(decryptedBuffer, HEADER_SIZE, decryptedBuffer.size - HEADER_SIZE)

            if (BinaryFormat.isMultiFile(dec)) {
                DecryptResult(multiFile = true, files = BinaryFormat.unpackMultipleFiles(dec))
            } else {
                val (name, data) = BinaryFormat.unpackSingleFile(dec)
                DecryptResult(multiFile = false, filename = name, data = data)
            }
        }

    fun isValidBuffer(bytes: ByteArray): Boolean {
        val prefixLen = 1 + NONCE_SIZE + SALT_SIZE
        val minSize = prefixLen + HEADER_SIZE + (TAG_LENGTH_BIT / 8)
        if (bytes.size < minSize) return false
        return bytes[0] == VERSION
    }

    data class DecryptResult(
        val multiFile: Boolean,
        val filename: String = "",
        val data: ByteArray = ByteArray(0),
        val files: List<FileEntry> = emptyList(),
    )
}
