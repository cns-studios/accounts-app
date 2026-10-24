package com.cns.accounts

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.cns.accounts.navigation.AppRoot
import com.cns.accounts.ui.lock.Biometrics
import com.cns.accounts.ui.theme.CNSAccountsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

// A FragmentActivity (not just ComponentActivity) because androidx.biometric's prompt needs one.
class MainActivity : FragmentActivity() {
    private val container get() = (application as CnsApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The approver key signs only after this prompt (Require Security App, see data/approvals).
        val prompt: suspend () -> Boolean = {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    Biometrics.authenticate(
                        this@MainActivity,
                        getString(R.string.approval_prompt_title),
                        getString(R.string.biometric_prompt_subtitle),
                        unlocksKey = true,
                        onSuccess = { if (cont.isActive) cont.resume(true) },
                        onFailure = { if (cont.isActive) cont.resume(false) },
                    )
                }
            }
        }
        container.verification.prompt = prompt
        verificationPrompt = prompt
        setContent {
            CNSAccountsTheme {
                AppRoot(container)
            }
        }
    }

    private var verificationPrompt: (suspend () -> Boolean)? = null

    override fun onStart() {
        super.onStart()
        container.appLock.onForeground(SystemClock.elapsedRealtime())
        container.onForeground()
    }

    override fun onDestroy() {
        if (container.verification.prompt === verificationPrompt) container.verification.prompt = null
        super.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        container.appLock.onBackground(SystemClock.elapsedRealtime())
    }
}
