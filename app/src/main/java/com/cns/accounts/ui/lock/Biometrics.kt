package com.cns.accounts.ui.lock

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Thin wrapper over androidx.biometric. Weak biometrics *or* the device PIN/pattern are accepted:
 * the lock only gates the UI of an app whose secrets are already Keystore-protected, and
 * BIOMETRIC_STRONG|DEVICE_CREDENTIAL isn't supported below API 30 (minSdk is 28).
 */
object Biometrics {
    private const val Authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(Authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(
        context: Context,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onFailure: () -> Unit = {},
    ) {
        val activity = context.findActivity() ?: return onFailure()
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFailure()
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(Authenticators)
                .build(),
        )
    }

    private tailrec fun Context.findActivity(): FragmentActivity? = when (this) {
        is FragmentActivity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
