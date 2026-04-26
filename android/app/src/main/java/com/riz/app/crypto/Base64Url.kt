package com.riz.app.crypto

import android.util.Base64

object Base64Url {
    private const val FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

    fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, FLAGS)

    fun decode(str: String): ByteArray = Base64.decode(str, FLAGS)
}
