package com.riz.app.crypto

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileNamingUtils {
    fun generateResultFilename(
        partNum: Int,
        totalParts: Int,
    ): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val dt = sdf.format(Date())
        return if (totalParts <= 1) {
            "readme_$dt.txt"
        } else {
            "readme_${dt}_${String.format(Locale.US, "%03d", partNum)}.txt"
        }
    }

    fun <T> sortFileParts(files: List<Pair<String, T>>): List<T>? {
        val regex = "_(\\d{3})\\.txt$".toRegex()
        val seq =
            files
                .mapNotNull { (name, original) ->
                    val match = regex.find(name)
                    if (match != null) Pair(match.groupValues[1].toInt(), original) else null
                }.sortedBy { it.first }

        return if (seq.size == files.size) seq.map { it.second } else null
    }
}
