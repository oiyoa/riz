package com.riz.app.crypto

import java.util.Locale
import kotlin.random.Random

object FileNamingUtils {
    private val SEQUENCE_REGEX = "_(\\d{3})\\.[^.]+$".toRegex()
    private val SEPARATORS = listOf("_", "-")
    private const val DECIMAL_DIGITS = 10

    fun generateResultFilenames(totalParts: Int): List<String> {
        val base = randomBaseName()
        val ext = FileExtensions.ALL.random()
        return if (totalParts <= 1) {
            listOf("$base.$ext")
        } else {
            List(totalParts) { i ->
                "${base}_${String.format(Locale.US, "%03d", i + 1)}.$ext"
            }
        }
    }

    fun <T> sortFileParts(files: List<Pair<String, T>>): List<T>? {
        val seq =
            files
                .mapNotNull { (name, original) ->
                    val match = SEQUENCE_REGEX.find(name)
                    if (match != null) Pair(match.groupValues[1].toInt(), original) else null
                }.sortedBy { it.first }

        return if (seq.size == files.size) seq.map { it.second } else null
    }

    private fun randomBaseName(): String {
        val tokenCount = Random.nextInt(1, 4)
        val separator = SEPARATORS.random()
        val tokens =
            buildList {
                add(PasswordPolicy.pickRandomWords(1).first())
                repeat(tokenCount - 1) {
                    if (Random.nextFloat() < 0.4f) {
                        add(randomDigits())
                    } else {
                        add(PasswordPolicy.pickRandomWords(1).first())
                    }
                }
            }
        return tokens.joinToString(separator)
    }

    private fun randomDigits(): String {
        val count = Random.nextInt(2, 7)
        return (1..count).joinToString("") { Random.nextInt(DECIMAL_DIGITS).toString() }
    }
}
