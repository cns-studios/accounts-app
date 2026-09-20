package com.cns.accounts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cns.accounts.navigation.AppNavGraph
import com.cns.accounts.ui.theme.CNSAccountsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CNSAccountsTheme {
                AppNavGraph()
            }
        }
    }
}
