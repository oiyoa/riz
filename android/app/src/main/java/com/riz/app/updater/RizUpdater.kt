package com.riz.app.updater

import android.content.Context
import android.util.Log
import com.oiyoa.android.frontedhttp.FrontedHttpClient
import com.oiyoa.android.updater.Updater
import com.oiyoa.android.updater.UpdaterConfig
import com.riz.app.BuildConfig
import com.riz.app.R

/**
 * Glue between Riz's BuildConfig (manifest URL, Ed25519 pubkey) and the
 * shared updater library. Called once from RizApplication.onCreate.
 *
 * Returns true when wired — gates the UI surface so dev builds without
 * the updater secrets configured don't render an empty banner.
 */
object RizUpdater {
    @Volatile private var enabled: Boolean = false

    fun isEnabled(): Boolean = enabled

    fun init(context: Context) {
        val config =
            UpdaterConfig.fromHex(
                manifestUrl = BuildConfig.UPDATER_MANIFEST_URL,
                pubKeyHex = BuildConfig.UPDATER_PUBKEY_HEX,
                keyId = BuildConfig.UPDATER_KEY_ID,
                appId = BuildConfig.APPLICATION_ID,
                currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
                installedVersionName = BuildConfig.VERSION_NAME,
                notificationSmallIconResId = R.mipmap.ic_launcher,
                httpClient = FrontedHttpClient.forAndroid(context),
            )
        if (config == null) {
            Log.i(TAG, "updater disabled — manifest URL or pubkey not configured / malformed")
            return
        }
        Updater.init(context, config)
        Updater.scheduleBackgroundChecks()
        enabled = true
        Log.i(TAG, "updater wired: ${BuildConfig.UPDATER_MANIFEST_URL.takeLast(MANIFEST_URL_LOG_TAIL)}")
    }

    private const val TAG = "RizUpdater"
    private const val MANIFEST_URL_LOG_TAIL = 20
}
