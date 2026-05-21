package com.riz.app.util

import android.util.Log
import com.riz.app.BuildConfig

/**
 * Logging helper that emits to logcat in debug builds only.
 */
object AppLog {
    fun e(
        tag: String,
        msg: String,
        tr: Throwable? = null,
    ) {
        if (BuildConfig.DEBUG) {
            if (tr != null) Log.e(tag, msg, tr) else Log.e(tag, msg)
        }
    }

    fun w(
        tag: String,
        msg: String,
        tr: Throwable? = null,
    ) {
        if (BuildConfig.DEBUG) {
            if (tr != null) Log.w(tag, msg, tr) else Log.w(tag, msg)
        }
    }
}
