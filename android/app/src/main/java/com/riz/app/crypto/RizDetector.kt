package com.riz.app.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ln

object RizDetector {
    sealed class Result {
        object NotRiz : Result()

        object MaybeRiz : Result()

        object Riz : Result()
    }

    private val MIN_BASE64_URL_LEN = ((CryptoEngine.MIN_CIPHERTEXT_SIZE * 4) + 2) / 3
    private val BASE64_URL_REGEX = Regex("^[A-Za-z0-9_-]+$")

    // AEAD output sits ~7.9 bits/byte; UTF-8 prose is ~4–5. 7.5 rules out plain content while
    // accepting any high-entropy blob (Riz, other AEADs, JPEG, ZIP).
    private const val ENTROPY_THRESHOLD = 7.5
    private const val ENTROPY_SAMPLE_BYTES = 4096
    private const val BYTE_VALUES = 256

    fun screenMessageText(text: String): Result {
        val trimmed = text.trim()
        if (trimmed.length < MIN_BASE64_URL_LEN) return Result.NotRiz
        if (!BASE64_URL_REGEX.matches(trimmed)) return Result.NotRiz
        return Result.MaybeRiz
    }

    suspend fun screenFileBytes(headBytes: ByteArray): Result =
        withContext(Dispatchers.Default) {
            if (headBytes.size < CryptoEngine.MIN_CIPHERTEXT_SIZE) return@withContext Result.NotRiz
            val sampleLen = minOf(ENTROPY_SAMPLE_BYTES, headBytes.size)
            if (shannonEntropy(headBytes, sampleLen) < ENTROPY_THRESHOLD) return@withContext Result.NotRiz
            Result.MaybeRiz
        }

    suspend fun probeMessage(
        text: String,
        password: String,
    ): Result {
        val trimmed = text.trim()
        if (screenMessageText(trimmed) == Result.NotRiz) return Result.NotRiz
        val decoded =
            try {
                Base64Url.decode(trimmed)
            } catch (_: IllegalArgumentException) {
                return Result.NotRiz
            }
        return if (CryptoEngine.probeIsRiz(decoded, password)) Result.Riz else Result.NotRiz
    }

    suspend fun probeFile(
        headBytes: ByteArray,
        password: String,
    ): Result = if (CryptoEngine.probeIsRiz(headBytes, password)) Result.Riz else Result.NotRiz

    private fun shannonEntropy(
        data: ByteArray,
        len: Int,
    ): Double {
        val freq = IntArray(BYTE_VALUES)
        for (i in 0 until len) freq[data[i].toInt() and 0xFF]++
        var h = 0.0
        val total = len.toDouble()
        val ln2 = ln(2.0)
        for (f in freq) {
            if (f == 0) continue
            val p = f / total
            h -= p * (ln(p) / ln2)
        }
        return h
    }
}
