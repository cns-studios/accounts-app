package com.cns.accounts.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cns.accounts.AppContainer
import com.cns.accounts.R
import com.cns.accounts.data.session.AuthState
import com.cns.accounts.ui.home.HomeScreen
import com.cns.accounts.ui.home.HomeViewModel
import com.cns.accounts.ui.lock.Biometrics
import com.cns.accounts.ui.lock.LockScreen
import com.cns.accounts.ui.login.LoginScreen
import com.cns.accounts.ui.login.LoginViewModel
import com.cns.accounts.ui.settings.SettingsDialogKind
import com.cns.accounts.ui.settings.SettingsScreen
import com.cns.accounts.ui.settings.SettingsViewModel

/**
 * The app root: sign-in while there is no session, the Home/Settings graph while there is,
 * the biometric lock on top when it applies, and the snackbar host for [AppContainer.messages].
 */
@Composable
fun AppRoot(container: AppContainer) {
    val authState by container.session.authState.collectAsStateWithLifecycle()
    val locked by container.appLock.locked.collectAsStateWithLifecycle()
    val biometricUnlock by container.preferences.biometricUnlock.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        container.messages.messages.collect { message -> snackbarHostState.showSnackbar(message.resolve(context)) }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        when (authState) {
            AuthState.SignedOut -> LoginRoute()
            AuthState.SignedIn -> AppNavGraph(container)
        }
        if (authState == AuthState.SignedIn && biometricUnlock && locked) {
            LockScreen(onUnlocked = container.appLock::unlock)
        }
        SnackbarHost(
            hostState = snackbarHostState,
            // Clear of Home's floating bottom nav (54dp tall, 24dp off the bottom edge).
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 96.dp),
        )
    }
}

@Composable
private fun LoginRoute() {
    val viewModel = containerViewModel { container, _ -> LoginViewModel(container) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LoginScreen(
        state = state,
        onUsernameChange = viewModel::updateUsername,
        onPasswordChange = viewModel::updatePassword,
        onCodeChange = viewModel::updateCode,
        onSubmitCredentials = viewModel::submitCredentials,
        onSubmitCode = viewModel::submitCode,
        onRestart = viewModel::restart,
    )
}

@Composable
private fun AppNavGraph(container: AppContainer) {
    val navController = rememberNavController()
    val viewModel = containerViewModel { c, _ -> HomeViewModel(c) }
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
                onOpenSetting = { id ->
                    navController.navigate("settings?highlight=$id") { launchSingleTop = true }
                },
                onRefresh = viewModel::refresh,
                onRefreshHistory = viewModel::refreshHistory,
                onLoadMoreHistory = viewModel::loadMoreHistory,
                onApproveRequest = viewModel::approveRequest,
                onDenyRequest = viewModel::denyRequest,
                onSetUpTwoFactor = viewModel::setUpTwoFactor,
                onTransferTwoFactor = viewModel::showTransferTwoFactor,
                onServiceClick = viewModel::showDisconnectApp,
                onDialogFieldChange = viewModel::updateDialogField,
                onDialogConfirm = viewModel::confirmDialog,
                onDialogDismiss = viewModel::dismissDialog,
            )
        }
        composable(
            route = "settings?highlight={highlight}",
            arguments = listOf(navArgument("highlight") { type = NavType.StringType; nullable = true }),
        ) { entry ->
            SettingsRoute(
                container,
                highlight = entry.arguments?.getString("highlight"),
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/** Settings plus the platform pieces its actions need: photo picker and biometric prompt. */
@Composable
private fun SettingsRoute(container: AppContainer, highlight: String?, onBack: () -> Unit) {
    val viewModel = containerViewModel { c, application -> SettingsViewModel(c, application) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.uploadAvatar(uri)
    }
    val biometricTitle = stringResource(R.string.enable_biometric_prompt_title)
    val biometricSubtitle = stringResource(R.string.biometric_prompt_subtitle)
    SettingsScreen(
        state = state,
        onBack = onBack,
        onToggleBiometricUnlock = { enabled ->
            when {
                !enabled -> viewModel.setBiometricUnlock(false)
                !Biometrics.isAvailable(context) -> container.messages.show(R.string.biometric_unavailable)
                // Confirm the user can actually unlock before turning the lock on.
                else -> Biometrics.authenticate(context, biometricTitle, biometricSubtitle, onSuccess = { viewModel.setBiometricUnlock(true) })
            }
        },
        onToggleNotifications = viewModel::setNotifications,
        onToggleTwoFactorAuthentication = viewModel::setTwoFactorAuthentication,
        onToggleRequireSecurityApp = viewModel::setRequireSecurityApp,
        onEditUsername = { viewModel.openDialog(SettingsDialogKind.EditUsername) },
        onChangeAvatar = { viewModel.openDialog(SettingsDialogKind.ChangeAvatar) },
        onRemoveAvatar = { viewModel.openDialog(SettingsDialogKind.RemoveAvatar) },
        onConfigureTwoFactor = viewModel::configureTwoFactor,
        onSetupRecoveryPhrase = viewModel::setupRecoveryPhrase,
        onChangePassword = { viewModel.openDialog(SettingsDialogKind.ChangePassword) },
        onLogOut = viewModel::logOut,
        onDeleteAccount = { viewModel.openDialog(SettingsDialogKind.DeleteAccount) },
        onDialogFieldChange = viewModel::updateField,
        onDialogConfirm = viewModel::confirmDialog,
        onDialogDismiss = viewModel::dismissDialog,
        onPickAvatar = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        highlight = highlight,
    )
}
