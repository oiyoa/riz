package com.riz.app.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Thin wrapper around AndroidX BiometricPrompt for one-shot device-credential
 * or strong-biometric authentication. Used to gate viewing the saved
 * passphrase in the password settings sheet — the long-term secret should not
 * be flashed onto the screen just because someone has the unlocked device for
 * a moment.
 */
object BiometricAuth {
    private val ALLOWED =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /** True if the device has at least a screen lock that we can prompt against. */
    fun isAvailable(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(ALLOWED) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onFailure: () -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt =
            BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onSuccess()
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        // User cancel / no hardware / etc — treat as failure.
                        onFailure()
                    }

                    override fun onAuthenticationFailed() {
                        // Individual attempt failed; the system continues prompting.
                        // No-op so the user can retry.
                    }
                },
            )

        val infoBuilder =
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(ALLOWED)

        runCatching { prompt.authenticate(infoBuilder.build()) }
            .onFailure { onFailure() }
    }
}
