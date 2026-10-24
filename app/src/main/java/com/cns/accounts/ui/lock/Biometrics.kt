package com.cns.accounts.ui.lock

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
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
        // For a Keystore key bound to user authentication (the approver key): only a strong
        // biometric or the screen lock unlock those. Strong-or-credential needs API 30; before
        // that the prompt is the same as the app lock's and a weak biometric fails at signing.
        unlocksKey: Boolean = false,
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
                .setAllowedAuthenticators(
                    if (unlocksKey && android.os.Build.VERSION.SDK_INT >= 30) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else Authenticators,
                )
                .build(),
        )
    }

    private tailrec fun Context.findActivity(): FragmentActivity? = when (this) {
        is FragmentActivity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
