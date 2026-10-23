package com.cns.accounts

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.cns.accounts.navigation.AppRoot
import com.cns.accounts.ui.theme.CNSAccountsTheme

// A FragmentActivity (not just ComponentActivity) because androidx.biometric's prompt needs one.
class MainActivity : FragmentActivity() {
    private val container get() = (application as CnsApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CNSAccountsTheme {
                AppRoot(container)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.appLock.onForeground(SystemClock.elapsedRealtime())
    }

    override fun onStop() {
        super.onStop()
        container.appLock.onBackground(SystemClock.elapsedRealtime())
    }
}
