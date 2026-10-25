package com.cns.accounts.navigation

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
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
import com.cns.accounts.ui.components.AppToastHost
import com.cns.accounts.ui.components.LocalDialogsShown
import com.cns.accounts.ui.components.SplashOverlay
import com.cns.accounts.ui.components.ToastState
import com.cns.accounts.ui.home.HomeScreen
import com.cns.accounts.ui.home.HomeViewModel
import com.cns.accounts.ui.lock.Biometrics
import com.cns.accounts.ui.lock.LockScreen
import com.cns.accounts.ui.login.LoginScreen
import com.cns.accounts.ui.login.LoginViewModel
import com.cns.accounts.ui.settings.AppSettingsScreen
import com.cns.accounts.ui.settings.AppSettingsViewModel
import com.cns.accounts.ui.settings.RecoveryPhraseScreen
import com.cns.accounts.ui.settings.RecoveryPhraseViewModel
import com.cns.accounts.ui.settings.SettingsDialogKind
import com.cns.accounts.ui.settings.SettingsScreen
import com.cns.accounts.ui.settings.SettingsViewModel
import com.cns.accounts.ui.settings.isAppSettingsId

/**
 * The app root: sign-in while there is no session, the Home/Settings graph while there is,
 * the biometric lock on top when it applies, the launch splash over everything while the app
 * (and, signed in, the account) loads, and the toast host for [AppContainer.messages].
 */
@Composable
fun AppRoot(container: AppContainer) {
    val authState by container.session.authState.collectAsStateWithLifecycle()
    val locked by container.appLock.locked.collectAsStateWithLifecycle()
    val biometricUnlock by container.preferences.biometricUnlock.collectAsStateWithLifecycle()
    val toastState = remember { ToastState() }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        container.messages.messages.collect { message -> toastState.show(message.resolve(context), message.isError) }
    }
    // Activity-scoped, so this is the same instance the nav graph uses.
    val homeViewModel = containerViewModel { c, _ -> HomeViewModel(c) }
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val signedIn = authState == AuthState.SignedIn
    val isLocked = signedIn && biometricUnlock && locked
    SplashOverlay(
        loading = signedIn && homeState.isLoadingAccount,
        sessionKey = authState.takeIf { signedIn },
    ) {
        // Sign-in and the app swap with a short fade-through, not a hard cut.
        AnimatedContent(
            targetState = signedIn,
            transitionSpec = {
                (fadeIn(tween(260, delayMillis = 60)) + scaleIn(tween(320, easing = EmphasizedDecelerate), initialScale = 0.96f))
                    .togetherWith(fadeOut(tween(160)))
            },
            label = "auth-state",
        ) { isSignedIn ->
            if (isSignedIn) {
                CompositionLocalProvider(LocalDialogsShown provides !isLocked) {
                    AppNavGraph(container, homeViewModel, codeHapticsAllowed = !isLocked)
                }
            } else {
                LoginRoute()
            }
        }
        if (isLocked) {
            LockScreen(onUnlocked = container.appLock::unlock)
        }
        AppToastHost(
            state = toastState,
            // Clear of Home's floating bottom nav (54dp tall, 24dp off the bottom edge).
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
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
        onShowCredentials = viewModel::showCredentials,
        onSubmitCredentials = viewModel::submitCredentials,
        onSubmitCode = viewModel::submitCode,
        onBack = viewModel::back,
        onUseCodeInstead = viewModel::useCodeInstead,
        onUsePasskey = viewModel::usePasskey,
        onShowRecovery = viewModel::showRecovery,
        onPhraseChange = viewModel::updatePhrase,
        onNewPasswordChange = viewModel::updateNewPassword,
        onNewPasswordConfirmChange = viewModel::updateNewPasswordConfirm,
        onSubmitRecovery = viewModel::submitRecovery,
    )
}

// Material's "emphasized decelerate": quick off the mark, long soft landing.
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

// Page pushes: the new page slides in over the old one, which drifts a quarter of the way out
// and dims. Springs, not fixed durations, so a fling-fast back gesture still lands cleanly.
private val PageSpring = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 520f, visibilityThreshold = IntOffset.VisibilityThreshold)
private const val UnderlayAlpha = 0.3f

@Composable
private fun AppNavGraph(container: AppContainer, viewModel: HomeViewModel, codeHapticsAllowed: Boolean) {
    val navController = rememberNavController()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NavHost(
        navController = navController,
        startDestination = "home",
        enterTransition = { slideInHorizontally(PageSpring) { it } },
        exitTransition = { slideOutHorizontally(PageSpring) { -it / 4 } + fadeOut(tween(300), targetAlpha = UnderlayAlpha) },
        popEnterTransition = { slideInHorizontally(PageSpring) { -it / 4 } + fadeIn(tween(300), initialAlpha = UnderlayAlpha) },
        popExitTransition = { slideOutHorizontally(PageSpring) { it } },
    ) {
        composable("home") {
            HomeScreen(
                state = state,
                onDestinationSelected = { destination ->
                    viewModel.selectDestination(destination)
                    if (destination != "home") navController.navigate(destination)
                },
                onSettingsClick = {
                    navController.navigate("app-settings") { launchSingleTop = true }
                },
                onAccountClick = {
                    navController.navigate("settings") { launchSingleTop = true }
                },
                onOpenSetting = { id ->
                    val route = if (isAppSettingsId(id)) "app-settings" else "settings"
                    navController.navigate("$route?highlight=$id") { launchSingleTop = true }
                },
                onRefresh = viewModel::refresh,
                onRefreshHistory = viewModel::refreshHistory,
                onLoadMoreHistory = viewModel::loadMoreHistory,
                onHistorySeen = viewModel::markHistorySeen,
                onApproveRequest = viewModel::approveRequest,
                onDenyRequest = viewModel::denyRequest,
                onSetUpTwoFactor = viewModel::setUpTwoFactor,
                onTransferTwoFactor = viewModel::showTransferTwoFactor,
                onToggleService = viewModel::toggleService,
                onDisconnectService = viewModel::showDisconnectApp,
                onSetUpService = viewModel::showSetUpService,
                onApproveServiceLogin = viewModel::approveServiceLogin,
                onDenyServiceLogin = viewModel::denyServiceLogin,
                onDialogFieldChange = viewModel::updateDialogField,
                onDialogConfirm = viewModel::confirmDialog,
                onDialogDismiss = viewModel::dismissDialog,
                codeHapticsAllowed = codeHapticsAllowed,
            )
        }
        composable(
            route = "settings?highlight={highlight}",
            arguments = listOf(navArgument("highlight") { type = NavType.StringType; nullable = true }),
        ) { entry ->
            SettingsRoute(
                highlight = entry.arguments?.getString("highlight"),
                onBack = { navController.popBackStack() },
                onOpenRecoveryPhrase = { navController.navigate("recovery-phrase") { launchSingleTop = true } },
            )
        }
        composable("recovery-phrase") {
            RecoveryPhraseRoute(onBack = { navController.popBackStack() })
        }
        composable(
            route = "app-settings?highlight={highlight}",
            arguments = listOf(navArgument("highlight") { type = NavType.StringType; nullable = true }),
        ) { entry ->
            AppSettingsRoute(
                container,
                highlight = entry.arguments?.getString("highlight"),
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/** Your Account plus the photo picker its avatar action needs. */
@Composable
private fun SettingsRoute(highlight: String?, onBack: () -> Unit, onOpenRecoveryPhrase: () -> Unit) {
    val viewModel = containerViewModel { c, application -> SettingsViewModel(c, application) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.uploadAvatar(uri)
    }
    SettingsScreen(
        state = state,
        onBack = onBack,
        onToggleTwoFactorAuthentication = viewModel::setTwoFactorAuthentication,
        onToggleRequireSecurityApp = viewModel::setRequireSecurityApp,
        onEditUsername = { viewModel.openDialog(SettingsDialogKind.EditUsername) },
        onChangeAvatar = { viewModel.openDialog(SettingsDialogKind.ChangeAvatar) },
        onRemoveAvatar = { viewModel.openDialog(SettingsDialogKind.RemoveAvatar) },
        onConfigureTwoFactor = viewModel::configureTwoFactor,
        onSetupRecoveryPhrase = onOpenRecoveryPhrase,
        onChangePassword = { viewModel.openDialog(SettingsDialogKind.ChangePassword) },
        onDeleteAccount = { viewModel.openDialog(SettingsDialogKind.DeleteAccount) },
        onDialogFieldChange = viewModel::updateField,
        onDialogConfirm = viewModel::confirmDialog,
        onDialogDismiss = viewModel::dismissDialog,
        onPickAvatar = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onSecurityFactorCelebrated = viewModel::onSecurityFactorCelebrated,
        highlight = highlight,
    )
}

/** The recovery phrase page; its ViewModel (and with it the shown words) goes when the page is left. */
@Composable
private fun RecoveryPhraseRoute(onBack: () -> Unit) {
    val viewModel = containerViewModel { c, _ -> RecoveryPhraseViewModel(c) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    RecoveryPhraseScreen(
        state = state,
        onBack = onBack,
        onPasswordChange = viewModel::updatePassword,
        onCodeChange = viewModel::updateCode,
        onSubmitVerify = viewModel::submitVerify,
        onNewPhrase = viewModel::startNewPhrase,
        onRemove = viewModel::startRemove,
        onReveal = viewModel::reveal,
        onSaveFile = { uri, text -> viewModel.savePhraseFile(context.contentResolver, uri, text) },
        onSavedItChange = viewModel::setSavedIt,
        onContinueToConfirm = viewModel::toConfirm,
        onAnswerChange = viewModel::updateAnswer,
        onSubmitConfirm = viewModel::submitConfirm,
        onStepBack = viewModel::back,
    )
}

/** The app Settings page plus the platform pieces its toggles need: biometric prompt and notification permission. */
@Composable
private fun AppSettingsRoute(container: AppContainer, highlight: String?, onBack: () -> Unit) {
    val viewModel = containerViewModel { c, _ -> AppSettingsViewModel(c) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Android 13+ asks before an app may post notifications.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.setNotifications(true) else container.messages.show(R.string.notifications_blocked, isError = true)
    }
    val biometricTitle = stringResource(R.string.enable_biometric_prompt_title)
    val biometricSubtitle = stringResource(R.string.biometric_prompt_subtitle)
    AppSettingsScreen(
        state = state,
        onBack = onBack,
        onToggleBiometricUnlock = { enabled ->
            when {
                !enabled -> viewModel.setBiometricUnlock(false)
                !Biometrics.isAvailable(context) -> container.messages.show(R.string.biometric_unavailable, isError = true)
                // Confirm the user can actually unlock before turning the lock on.
                else -> Biometrics.authenticate(context, biometricTitle, biometricSubtitle, onSuccess = { viewModel.setBiometricUnlock(true) })
            }
        },
        onToggleNotifications = { enabled ->
            if (enabled && Build.VERSION.SDK_INT >= 33 && !container.notifier.canPost()) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.setNotifications(enabled)
            }
        },
        onLogOut = viewModel::logOut,
        onCheckForUpdates = viewModel::checkForUpdates,
        onInstallUpdate = viewModel::installUpdate,
        onLockDelayChange = viewModel::setLockDelay,
        onToggleEvent = viewModel::setEventNotifications,
        onLanguageChange = { tag ->
            viewModel.setLanguage(tag)
            // Every context picks the language up when it is created: rebuild the activity.
            context.findActivity()?.recreate()
        },
        highlight = highlight,
    )
}

private fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
