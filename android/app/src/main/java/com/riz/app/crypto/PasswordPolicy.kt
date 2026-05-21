package com.riz.app.crypto

import android.content.Context
import com.nulabinc.zxcvbn.Zxcvbn
import com.riz.app.util.AppLog
import java.security.SecureRandom

/**
 * Password strength scoring + EFF Large Wordlist passphrase generator.
 */
object PasswordPolicy {
    const val MIN_SCORE = 3
    const val DEFAULT_PASSPHRASE_WORDS = 6
    private const val SEPARATOR = "-"
    private const val EXPECTED_WORDLIST_SIZE = 7776
    private const val WORDLIST_ASSET = "eff_large_wordlist.txt"

    /**
     * Cap input to zxcvbn at this length. Above 64 chars the analyser cost
     * grows steeply and any password that long is already past the
     * unguessable threshold.
     */
    private const val STRENGTH_INPUT_CAP = 64

    /** 2^32 — used as the upper bound for unbiased 32-bit rejection sampling. */
    private const val UINT32_RANGE = 0x1_0000_0000L

    /** Mask to interpret Int.nextInt() as an unsigned 32-bit Long. */
    private const val UINT32_MASK = 0xFFFF_FFFFL

    private val zxcvbn = Zxcvbn()
    private val secureRandom = SecureRandom()
    private var words: List<String> = emptyList()

    fun init(context: Context) {
        if (words.isNotEmpty()) return
        try {
            words =
                context.assets.open(WORDLIST_ASSET).bufferedReader().use { reader ->
                    reader.readLines().filter { it.isNotBlank() }
                }
        } catch (e: Exception) {
            AppLog.e("PasswordPolicy", "Failed to load wordlist", e)
        }
    }

    data class Assessment(
        val score: Int,
        val warning: String,
        val suggestions: List<String>,
    )

    fun assessStrength(password: String): Assessment {
        val sample =
            if (password.length > STRENGTH_INPUT_CAP) {
                password.substring(0, STRENGTH_INPUT_CAP)
            } else {
                password
            }
        val strength = zxcvbn.measure(sample)
        val feedback = strength.feedback
        val suggestions = feedback?.getSuggestions(java.util.Locale.ENGLISH).orEmpty()
        return Assessment(
            score = strength.score,
            warning = feedback?.getWarning(java.util.Locale.ENGLISH).orEmpty(),
            suggestions = suggestions,
        )
    }

    /**
     * Uniform-random passphrase via rejection sampling on 32-bit ints.
     */
    fun generatePassphrase(numWords: Int = DEFAULT_PASSPHRASE_WORDS): String = pickRandomWords(numWords).joinToString(SEPARATOR)

    fun pickRandomWords(numWords: Int): List<String> {
        check(words.size >= EXPECTED_WORDLIST_SIZE) {
            "Wordlist not loaded; call PasswordPolicy.init(context) first"
        }
        val n = words.size
        // Largest multiple of n that fits in an unsigned 32-bit int.
        val limit = (UINT32_RANGE / n) * n
        return List(numWords) {
            var r: Long
            do {
                r = secureRandom.nextInt().toLong() and UINT32_MASK
            } while (r >= limit)
            words[(r % n).toInt()]
        }
    }
}
