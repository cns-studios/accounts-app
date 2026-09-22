package com.cns.accounts.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cns.accounts.ui.home.HomeScreen
import com.cns.accounts.ui.home.HomeViewModel
import com.cns.accounts.ui.settings.SettingsScreen
import com.cns.accounts.ui.settings.SettingsViewModel

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val viewModel: HomeViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NavHost(
        navController = navController,
        startDestination = "home",
        // A short slide+fade (Material "shared axis" shape): enough travel to read as motion,
        // fast enough (200ms, decelerate easing) to feel snappy rather than sluggish. Applied at
        // the NavHost level so it's identical in both directions.
        enterTransition = {
            fadeIn(tween(200, easing = FastOutSlowInEasing)) +
                slideInHorizontally(tween(200, easing = FastOutSlowInEasing)) { fullWidth -> fullWidth / 6 }
        },
        exitTransition = {
            fadeOut(tween(150, easing = FastOutSlowInEasing)) +
                slideOutHorizontally(tween(150, easing = FastOutSlowInEasing)) { fullWidth -> -fullWidth / 6 }
        },
        popEnterTransition = {
            fadeIn(tween(200, easing = FastOutSlowInEasing)) +
                slideInHorizontally(tween(200, easing = FastOutSlowInEasing)) { fullWidth -> -fullWidth / 6 }
        },
        popExitTransition = {
            fadeOut(tween(150, easing = FastOutSlowInEasing)) +
                slideOutHorizontally(tween(150, easing = FastOutSlowInEasing)) { fullWidth -> fullWidth / 6 }
        },
    ) {
        composable("home") {
            HomeScreen(
                state = state,
                onDestinationSelected = { destination ->
                    viewModel.selectDestination(destination)
                    if (destination != "home") navController.navigate(destination)
                },
                onSettingsClick = {
                    navController.navigate("settings") { launchSingleTop = true }
                },
                onApproveRequest = viewModel::approveRequest,
                onDenyRequest = viewModel::denyRequest,
                onCopyAccountCode = viewModel::copyAccountCode,
            )
        }
        composable("profile") { PlaceholderScreen("Profile") }
        composable("search") { PlaceholderScreen("Search") }
        composable("settings") {
            val settingsViewModel: SettingsViewModel = viewModel()
            val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
            SettingsScreen(
                state = settingsState,
                onBack = { navController.popBackStack() },
                onToggleBiometricUnlock = settingsViewModel::setBiometricUnlock,
                onToggleStrongAccountProtection = settingsViewModel::setStrongAccountProtection,
                onToggleTwoFactorAuthentication = settingsViewModel::setTwoFactorAuthentication,
                onToggleRequireSecurityApp = settingsViewModel::setRequireSecurityApp,
                onEditUsername = settingsViewModel::editUsername,
                onChangeAvatar = settingsViewModel::changeAvatar,
                onRemoveAvatar = settingsViewModel::removeAvatar,
                onConfigureTwoFactor = settingsViewModel::configureTwoFactor,
                onSetupRecoveryPhrase = settingsViewModel::setupRecoveryPhrase,
                onChangePassword = settingsViewModel::changePassword,
                onLogOut = settingsViewModel::logOut,
                onDeleteAccount = settingsViewModel::deleteAccount,
            )
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.material3.Text(title)
    }
}
