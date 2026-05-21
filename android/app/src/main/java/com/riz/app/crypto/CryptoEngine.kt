package com.riz.app.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoEngine {
    private const val SALT_SIZE = 16
    private const val NONCE_SIZE = 12
    private const val ITERATIONS = 600000
    private const val TAG_LENGTH_BIT = 128
    private const val TAG_LENGTH_BYTES = TAG_LENGTH_BIT / 8
    private const val KEY_LENGTH_BIT = 256
    private const val ALGORITHM = "AES/GCM/NoPadding"

    // Wire layout: outer [nonce 12 | salt 16] cleartext; AEAD body [version 1 | flags 1 |
    // createdAt 8 | ...]. Version inside the AEAD so the file looks like random bytes on disk.
    private const val VERSION: Byte = 0x03

    private const val PREFIX_SIZE = NONCE_SIZE + SALT_SIZE
    private const val TIMESTAMP_SIZE = 8
    private const val INNER_HEADER_SIZE = 1 + 1 + TIMESTAMP_SIZE

    /** Smallest byte string that could be a valid Riz ciphertext. */
    const val MIN_CIPHERTEXT_SIZE = PREFIX_SIZE + INNER_HEADER_SIZE + TAG_LENGTH_BYTES

    private val HKDF_INFO = "riz/v1/message-key".toByteArray(Charsets.UTF_8)

    private const val FLAG_COMPRESSED: Byte = 0x01
    private const val FLAG_MULTI_FILE: Byte = 0x02
    private const val FLAGS_VALID_MASK = 0x03

    private const val MIN_CREATED_AT_MS = 1_704_067_200_000L
    private const val DETECTION_FUTURE_SLOP_MS = 86_400_000L

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
        hmac.init(SecretKeySpec(messageSalt, "HmacSHA256"))
        val prk = hmac.doFinal(masterKey.encoded)
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

            val cipher =
                Cipher.getInstance(ALGORITHM).apply {
                    init(Cipher.ENCRYPT_MODE, msgKey, GCMParameterSpec(TAG_LENGTH_BIT, nonce))
                }
            val plaintextLen = INNER_HEADER_SIZE + data.size
            val output = ByteArray(PREFIX_SIZE + cipher.getOutputSize(plaintextLen))

            System.arraycopy(nonce, 0, output, 0, NONCE_SIZE)
            System.arraycopy(salt, 0, output, NONCE_SIZE, SALT_SIZE)

            output[PREFIX_SIZE] = VERSION
            output[PREFIX_SIZE + 1] = flags
            ByteBuffer.wrap(output, PREFIX_SIZE + 2, TIMESTAMP_SIZE).putLong(System.currentTimeMillis())
            System.arraycopy(data, 0, output, PREFIX_SIZE + INNER_HEADER_SIZE, data.size)

            cipher.updateAAD(output, 0, PREFIX_SIZE)
            // In-place: Conscrypt GCM copies input to its buf before writing output, so overlap is safe.
            cipher.doFinal(output, PREFIX_SIZE, plaintextLen, output, PREFIX_SIZE)

            output
        }

    suspend fun encryptSingleFile(
        name: String,
        data: ByteArray,
        password: String,
        keyPool: DerivedKeyPool? = null,
    ): ByteArray {
        val compressed =
            withContext(Dispatchers.Default) {
                Compression.compressData(BinaryFormat.packSingleFile(name, data))
            }
        return encryptInternal(compressed, password, FLAG_COMPRESSED, keyPool)
    }

    suspend fun encryptMultiFiles(
        files: MutableList<FileEntry>,
        password: String,
        keyPool: DerivedKeyPool? = null,
    ): ByteArray {
        val compressed =
            withContext(Dispatchers.Default) {
                Compression.compressMultiFile(files)
            }
        files.clear()
        val flags = (FLAG_COMPRESSED.toInt() or FLAG_MULTI_FILE.toInt()).toByte()
        return encryptInternal(compressed, password, flags, keyPool)
    }

    suspend fun encryptMessage(
        text: String,
        password: String,
        keyPool: DerivedKeyPool? = null,
    ): ByteArray {
        val compressed =
            withContext(Dispatchers.Default) {
                Compression.compressData(BinaryFormat.packSingleFile("", text.toByteArray(Charsets.UTF_8)))
            }
        return encryptInternal(compressed, password, FLAG_COMPRESSED, keyPool)
    }

    suspend fun decryptBytes(
        packed: ByteArray,
        password: String,
    ): DecryptResult =
        withContext(Dispatchers.Default) {
            require(packed.size >= MIN_CIPHERTEXT_SIZE) { "Invalid ciphertext length" }

            val nonce = packed.copyOfRange(0, NONCE_SIZE)
            val salt = packed.copyOfRange(NONCE_SIZE, PREFIX_SIZE)

            val mKey = getMasterKey(password, salt)
            val msgKey = deriveMessageKey(mKey, nonce)

            val decryptedBuffer =
                Cipher.getInstance(ALGORITHM).run {
                    init(Cipher.DECRYPT_MODE, msgKey, GCMParameterSpec(TAG_LENGTH_BIT, nonce))
                    updateAAD(packed, 0, PREFIX_SIZE)
                    doFinal(packed, PREFIX_SIZE, packed.size - PREFIX_SIZE)
                }

            require(decryptedBuffer[0] == VERSION) { "Unrecognized inner version" }

            val createdAt = ByteBuffer.wrap(decryptedBuffer, 2, TIMESTAMP_SIZE).long

            val dec =
                Compression.decompressData(
                    decryptedBuffer,
                    INNER_HEADER_SIZE,
                    decryptedBuffer.size - INNER_HEADER_SIZE,
                )

            if (BinaryFormat.isMultiFile(dec)) {
                DecryptResult(
                    multiFile = true,
                    files = BinaryFormat.unpackMultipleFiles(dec),
                    createdAt = createdAt,
                )
            } else {
                val (name, data) = BinaryFormat.unpackSingleFile(dec)
                DecryptResult(
                    multiFile = false,
                    filename = name,
                    data = data,
                    createdAt = createdAt,
                )
            }
        }

    suspend fun probeIsRiz(
        packed: ByteArray,
        password: String,
    ): Boolean =
        withContext(Dispatchers.Default) {
            if (packed.size < MIN_CIPHERTEXT_SIZE) return@withContext false

            val nonce = packed.copyOfRange(0, NONCE_SIZE)
            val salt = packed.copyOfRange(NONCE_SIZE, PREFIX_SIZE)

            val mKey =
                try {
                    getMasterKey(password, salt)
                } catch (_: Exception) {
                    return@withContext false
                }
            val msgKey = deriveMessageKey(mKey, nonce)

            // GCM J0 = nonce || 0x00000001; first data block uses J0+1 as CTR IV.
            val counterBlock =
                ByteArray(16).apply {
                    System.arraycopy(nonce, 0, this, 0, NONCE_SIZE)
                    this[15] = 2
                }

            val plaintext =
                try {
                    Cipher.getInstance("AES/CTR/NoPadding").run {
                        init(Cipher.DECRYPT_MODE, msgKey, IvParameterSpec(counterBlock))
                        doFinal(packed, PREFIX_SIZE, 16)
                    }
                } catch (_: Exception) {
                    return@withContext false
                }

            if (plaintext[0] != VERSION) return@withContext false

            val flagsUnsigned = plaintext[1].toInt() and 0xFF
            if (flagsUnsigned and FLAGS_VALID_MASK.inv() != 0) return@withContext false

            val createdAt = ByteBuffer.wrap(plaintext, 2, TIMESTAMP_SIZE).long
            val maxCreatedAt = System.currentTimeMillis() + DETECTION_FUTURE_SLOP_MS
            createdAt in MIN_CREATED_AT_MS..maxCreatedAt
        }

    class DecryptResult(
        val multiFile: Boolean,
        val filename: String = "",
        val data: ByteArray = ByteArray(0),
        val files: List<FileEntry> = emptyList(),
        val createdAt: Long? = null,
    )
}
