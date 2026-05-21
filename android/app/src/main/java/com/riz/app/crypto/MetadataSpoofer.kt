package com.riz.app.crypto

import java.security.SecureRandom
import java.util.concurrent.TimeUnit

// The encrypted blob carries no internal timestamps, so the only metadata
// that could leak "when this file was made" is the filesystem's lastModified
// field. We override it with a random past timestamp so a snooper can't
// correlate result files with the moment the user actually saved them.
//
// The chosen range — between 7 days and 2 years ago — avoids two tells:
// future timestamps (instantly suspicious) and 1970-epoch dates (a known
// forensic giveaway). Each file gets its own draw, so multiple results from
// one session don't form a tight timestamp cluster either.
object MetadataSpoofer {
    private val secureRandom = SecureRandom()
    private val maxAgeMs = TimeUnit.DAYS.toMillis(365 * 2)
    private val minAgeMs = TimeUnit.DAYS.toMillis(7)

    fun randomPastTimestamp(): Long {
        val now = System.currentTimeMillis()
        val range = maxAgeMs - minAgeMs
        val offset = (secureRandom.nextLong() and Long.MAX_VALUE) % range
        return now - minAgeMs - offset
    }
}
