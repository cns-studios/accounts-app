package com.cns.accounts.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import com.cns.accounts.R
import com.cns.accounts.data.SearchResultKind
import com.cns.accounts.ui.search.ActivityResultPrefix
import com.cns.accounts.ui.search.SettingsResultPrefix
import com.cns.accounts.data.SignInRequest
import com.cns.accounts.ui.format.relativeTime
import com.cns.accounts.data.Service
import com.cns.accounts.data.TimerSquareCount
import com.cns.accounts.data.authenticator.Totp
import com.cns.accounts.data.authenticator.TwoFactorState
import com.cns.accounts.data.sampleHomeData
import com.cns.accounts.ui.components.ActionDialog
import com.cns.accounts.ui.components.DialogField
import com.cns.accounts.ui.components.FieldKind
import com.cns.accounts.ui.components.AppCard
import com.cns.accounts.ui.components.FLING_VELOCITY_THRESHOLD
import com.cns.accounts.ui.components.HomeBottomNavigation
import com.cns.accounts.ui.components.NothingPullToRefreshBox
import com.cns.accounts.ui.components.ProfileHeader
import com.cns.accounts.ui.components.TabSettleSpring
import com.cns.accounts.ui.components.TokenIcon
import com.cns.accounts.ui.components.TokenIconKind
import com.cns.accounts.ui.components.BusySpinnerPhase
import com.cns.accounts.ui.components.RefreshSpinner
import com.cns.accounts.ui.components.navAlignedWidth
import com.cns.accounts.ui.theme.AccountCodeText
import com.cns.accounts.ui.theme.CardHeadlineText
import com.cns.accounts.ui.theme.CardLabelText
import com.cns.accounts.ui.theme.CardSupportingText
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.LocalDimensions
import com.cns.accounts.ui.theme.LocalSpacing
import com.cns.accounts.ui.theme.RequestCaptionText
import com.cns.accounts.ui.theme.RequestMetaText
import com.cns.accounts.ui.theme.RequestTitleText

@Composable
fun HomeScreen(
    state: HomeUiState,
    onDestinationSelected: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onAccountClick: () -> Unit,
    onOpenSetting: (String) -> Unit,
    onRefresh: () -> Unit,
    onRefreshHistory: () -> Unit,
    onLoadMoreHistory: () -> Unit,
    onHistorySeen: () -> Unit,
    onApproveRequest: () -> Unit,
    onDenyRequest: () -> Unit,
    onSetUpTwoFactor: () -> Unit,
    onTransferTwoFactor: () -> Unit,
    onToggleService: (Service) -> Unit,
    onDisconnectService: (Service) -> Unit,
    onSetUpService: (Service) -> Unit,
    onApproveServiceLogin: (Service) -> Unit,
    onDenyServiceLogin: (Service) -> Unit,
    onDialogFieldChange: (Int, String) -> Unit,
    onDialogConfirm: () -> Unit,
    onDialogDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    // False while something covers Home (the lock screen): the code's rollover buzz stays quiet.
    codeHapticsAllowed: Boolean = true,
) {
    // Saveable: opening Settings from a search result and coming back finds Home as it was left.
    var selectedTab by rememberSaveable { androidx.compose.runtime.mutableStateOf("home") }
    // Home/history swipe: a drag on EITHER the segmented nav pill or the page background below
    // drives this same liveSwipeProgress/isSwipeDragging pair - see onSwipeDragStarted/
    // onSwipeSettle - so it doesn't matter which surface the user actually touches, and
    // settleProgress springs from wherever the drag was released (or a tab is tapped directly).
    // None of these are read during HomeScreen's composition, only via the swipeProgress lambda
    // from graphicsLayer/the nav pill, so a swipe or settle animation redraws the two pages
    // every frame instead of recomposing the whole screen (both pages included) every frame.
    var isSwipeDragging by remember { androidx.compose.runtime.mutableStateOf(false) }
    var liveSwipeProgress by remember { mutableFloatStateOf(0f) }
    val settleProgress = remember { Animatable(0f) }
    val swipeScope = rememberCoroutineScope()
    val swipeProgress: () -> Float = { if (isSwipeDragging) liveSwipeProgress else settleProgress.value }
    LaunchedEffect(selectedTab) {
        if (!isSwipeDragging) settleProgress.animateTo(if (selectedTab == "home") 0f else 1f, TabSettleSpring)
    }
    fun selectTab(tab: String) {
        selectedTab = tab
        if (tab == "home") onDestinationSelected(tab)
    }
    val onSwipeDragStarted: () -> Unit = {
        liveSwipeProgress = swipeProgress()
        isSwipeDragging = true
    }
    val onSwipeSettle: (Float) -> Unit = { velocity ->
        val settleToHistory = when {
            velocity > FLING_VELOCITY_THRESHOLD -> true
            velocity < -FLING_VELOCITY_THRESHOLD -> false
            else -> liveSwipeProgress > 0.5f
        }
        swipeScope.launch {
            settleProgress.snapTo(liveSwipeProgress)
            isSwipeDragging = false
            settleProgress.animateTo(if (settleToHistory) 1f else 0f, TabSettleSpring)
        }
        val newTab = if (settleToHistory) "history" else "home"
        if (newTab != selectedTab) selectTab(newTab)
    }
    var pageWidthPx by remember { mutableFloatStateOf(1f) }
    // Only the discrete "how many squares are spent" bucket is ever shown on screen, so this is
    // only written when that bucket actually changes (a handful of times per 30s cycle) rather
    // than on every 100ms tick - the ticks themselves still run every 100ms internally (for code
    // rotation + exact timing), they just don't all trigger recomposition of the account card.
    var spentCount by remember { mutableIntStateOf(0) }
    val twoFactor = state.data.twoFactor
    val secret = (twoFactor as? TwoFactorState.Active)?.secret
    val secretBytes = remember(secret) { secret?.let(Totp::decodeBase32) }
    var accountCode by remember(secretBytes) {
        androidx.compose.runtime.mutableStateOf(secretBytes?.let { formatAccountCode(Totp.code(it, System.currentTimeMillis())) }.orEmpty())
    }
    var searchActive by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var searchQuery by rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    // An activity result opens History on its event; Back from there reopens the search.
    var historyFocusId by remember { androidx.compose.runtime.mutableStateOf<Long?>(null) }
    var searchToReturnTo by rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    fun closeSearch() {
        searchQuery = ""
        searchActive = false
    }
    // Android Back walks back through Home instead of leaving the app: search, then History, then out.
    BackHandler(enabled = searchActive) {
        searchToReturnTo = null
        closeSearch()
    }
    BackHandler(enabled = !searchActive && selectedTab == "history") {
        val query = searchToReturnTo
        searchToReturnTo = null
        if (query != null) {
            searchQuery = query
            searchActive = true
        } else {
            selectTab("home")
        }
    }
    val searchAlpha = animateFloatAsState(
        targetValue = if (searchActive) 1f else 0f,
        animationSpec = tween(280),
        label = "search-overlay-alpha",
    )
    // Only the visible/hidden flip is read during composition; the per-frame alpha itself is
    // read in graphicsLayer, so the fade doesn't recompose the whole screen every frame.
    val searchOverlayShown by remember { derivedStateOf { searchAlpha.value > 0.01f } }
    val haptic = LocalHapticFeedback.current
    // The rollover buzz is only for someone looking at the code: Home's own tab, in the
    // foreground, with nothing (search, a dialog, the lock screen) over it.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val codeHapticsOn by rememberUpdatedState(
        codeHapticsAllowed && lifecycleState.isAtLeast(Lifecycle.State.RESUMED) &&
            selectedTab == "home" && !searchActive && state.dialog == null,
    )
    // History on screen: its events count as seen, so the nav's unread mark and their
    // notifications go away (also for new ones a refresh brings in while the tab is open).
    LaunchedEffect(selectedTab, state.historyUnread) {
        if (selectedTab == "history") {
            delay(HistorySeenDelayMillis)
            onHistorySeen()
        }
    }
    val secretLength = TimerSquareCount
    // TOTP periods are aligned to wall-clock 30s boundaries, so the timer squares are too: the
    // code flips exactly when the row resets, matching any other authenticator app.
    LaunchedEffect(secretBytes) {
        if (secretBytes == null) return@LaunchedEffect
        var lastPeriod = System.currentTimeMillis() / Totp.PeriodMillis
        while (true) {
            delay(100L)
            val now = System.currentTimeMillis()
            val period = now / Totp.PeriodMillis
            if (period != lastPeriod) {
                lastPeriod = period
                if (secretBytes != null) {
                    accountCode = formatAccountCode(Totp.code(secretBytes, now))
                    if (codeHapticsOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
            val cycleMillis = now % Totp.PeriodMillis
            val newSpentCount = ((cycleMillis + 100) * secretLength / Totp.PeriodMillis.toFloat()).toInt().coerceIn(0, secretLength)
            if (newSpentCount != spentCount) {
                spentCount = newSpentCount
            }
        }
    }
    val dimensions = LocalDimensions.current
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxSize()
            .background(LocalAppColors.current.background)
            .navigationBarsPadding()
            .onSizeChanged { pageWidthPx = it.width.toFloat() }
            .draggable(
                orientation = Orientation.Horizontal,
                enabled = !searchActive,
                state = rememberDraggableState { delta ->
                    liveSwipeProgress = (liveSwipeProgress - delta / pageWidthPx).coerceIn(0f, 1f)
                },
                onDragStarted = { onSwipeDragStarted() },
                onDragStopped = { velocity -> onSwipeSettle(-velocity) },
            ),
    ) {
        NothingPullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val progress = swipeProgress()
                    translationX = -progress * size.width
                },
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 62.dp,
                    bottom = dimensions.navHeight + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                item(key = "profile", contentType = "header") {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = NavSideInset),
                        contentAlignment = Alignment.Center,
                    ) {
                        ProfileHeader(state.data.profile, onSettingsClick, modifier = Modifier.navAlignedWidth())
                    }
                }
                item(key = "profile-request-gap", contentType = "spacer") {
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 24.dp))
                }
                // Only shown while a request waits for this device's approval (Require Security App).
                val signInRequest = state.data.signInRequest
                if (signInRequest != null) {
                    item(key = "request", contentType = "request") {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = NavSideInset),
                            contentAlignment = Alignment.Center,
                        ) {
                            SignInRequestCard(signInRequest, onApproveRequest, onDenyRequest, modifier = Modifier.navAlignedWidth())
                        }
                    }
                    item(key = "request-account-gap", contentType = "spacer") {
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 17.dp))
                    }
                }
                item(key = "account", contentType = "account") {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = NavSideInset),
                        contentAlignment = Alignment.Center,
                    ) {
                        AccountCard(
                            twoFactor, accountCode, spentCount, state.isSettingUpTwoFactor,
                            onSetUp = onSetUpTwoFactor, onTransfer = onTransferTwoFactor, modifier = Modifier.navAlignedWidth(),
                        )
                    }
                }
                item(key = "account-services-gap", contentType = "spacer") {
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 17.dp))
                }
                item(key = "services", contentType = "services") {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = NavSideInset),
                        contentAlignment = Alignment.Center,
                    ) {
                        ServicesCard(
                            modifier = Modifier.navAlignedWidth(),
                            services = state.data.services,
                            expandedServiceId = state.expandedServiceId,
                            onToggleService = onToggleService,
                            onDisconnect = onDisconnectService,
                            onSetUp = onSetUpService,
                            onApproveLogin = onApproveServiceLogin,
                            onDenyLogin = onDenyServiceLogin,
                        )
                    }
                }
            }
        }
        com.cns.accounts.ui.history.HistoryScreen(
            state = state.history,
            onRefresh = onRefreshHistory,
            onLoadMore = onLoadMoreHistory,
            focusEventId = historyFocusId,
            onFocusShown = { historyFocusId = null },
            modifier = Modifier.graphicsLayer {
                val progress = swipeProgress()
                translationX = (1f - progress) * size.width
            },
        )
        // search.tokens.json: an opaque full-screen overlay above Home/History while search is
        // active; closing it (the bar's X) just fades it away, revealing whichever tab was
        // showing underneath - Home or History are untouched, not navigated away from.
        if (searchActive || searchOverlayShown) {
            val searchResults = com.cns.accounts.ui.search.rememberSearchResults(searchQuery, state.history.events)
            com.cns.accounts.ui.search.SearchScreen(
                query = searchQuery,
                results = searchResults,
                onResultClick = { result ->
                    when (result.kind) {
                        SearchResultKind.Activity -> {
                            searchToReturnTo = searchQuery
                            closeSearch()
                            historyFocusId = result.id.removePrefix(ActivityResultPrefix).toLongOrNull()
                            selectTab("history")
                        }
                        // The search stays open underneath, so Back from Settings returns to it.
                        SearchResultKind.Settings -> onOpenSetting(result.id.removePrefix(SettingsResultPrefix))
                        else -> onAccountClick()
                    }
                },
                modifier = Modifier.graphicsLayer { alpha = searchAlpha.value },
            )
        }
        HomeBottomNavigation(
            selectedTab = selectedTab,
            onTabSelected = ::selectTab,
            swipeProgress = swipeProgress,
            onSwipeDragStarted = onSwipeDragStarted,
            onSwipeProgressDelta = { delta -> liveSwipeProgress = (liveSwipeProgress + delta).coerceIn(0f, 1f) },
            onSwipeDragStopped = onSwipeSettle,
            onUserClick = onAccountClick,
            avatarUrl = state.data.profile.avatarUrl,
            historyUnread = state.historyUnread,
            isSearchActive = searchActive,
            onSearchActiveChange = {
                searchActive = it
                if (it) searchToReturnTo = null
            },
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .padding(start = NavSideInset, end = NavSideInset, bottom = dimensions.navBottomGap),
        )
    }
    HomeDialogHost(state.dialog, onDialogFieldChange, onDialogConfirm, onDialogDismiss)
}

@Composable
private fun HomeDialogHost(
    dialog: HomeDialog?,
    onFieldChange: (Int, String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        is HomeDialog.TransferTwoFactor -> ActionDialog(
            title = stringResource(R.string.transfer_2fa_title),
            subtitle = stringResource(R.string.transfer_2fa_subtitle),
            primaryButtonLabel = stringResource(R.string.transfer),
            fields = listOf(
                DialogField(stringResource(R.string.password_placeholder), dialog.password, FieldKind.Password),
                DialogField(stringResource(R.string.transfer_2fa_code_placeholder), dialog.code, FieldKind.Code),
            ),
            onFieldChange = onFieldChange,
            errorText = dialog.error?.resolve(LocalContext.current),
            isBusy = dialog.isBusy,
            onDismissRequest = onDismiss,
            onPrimaryAction = onConfirm,
        )
        is HomeDialog.DisconnectApp -> ActionDialog(
            title = stringResource(R.string.disconnect_app_title),
            subtitle = stringResource(R.string.disconnect_app_subtitle, dialog.service.name),
            primaryButtonLabel = stringResource(R.string.disconnect),
            isBusy = dialog.isBusy,
            onDismissRequest = onDismiss,
            onPrimaryAction = onConfirm,
        )
        is HomeDialog.SetUpService -> if (dialog.waitingCode != null) {
            // Waiting for a trusted Sendly device; the button withdraws the request.
            ActionDialog(
                title = stringResource(R.string.sendly_waiting_title),
                subtitle = stringResource(R.string.sendly_waiting_subtitle),
                detail = dialog.waitingCode.chunked(3).joinToString(" "),
                primaryButtonLabel = stringResource(R.string.abort),
                onDismissRequest = onDismiss,
                onPrimaryAction = onConfirm,
            )
        } else {
            ActionDialog(
                title = stringResource(R.string.set_up_service_title, dialog.service.name),
                subtitle = stringResource(R.string.set_up_sendly_subtitle),
                primaryButtonLabel = stringResource(R.string.continue_label),
                errorText = dialog.error?.resolve(LocalContext.current),
                isBusy = dialog.isBusy,
                onDismissRequest = onDismiss,
                onPrimaryAction = onConfirm,
            )
        }
        null -> Unit
    }
}

@Composable
private fun SignInRequestCard(
    request: SignInRequest,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    // Sized to content (no fixed card height) so the bottom gap below the buttons is exactly
    // the padding value below, matching the left/right inset instead of leftover centering slack.
    AppCard(
        color = colors.accent,
        modifier = modifier.semantics { role = Role.Button },
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(RequestCardPadding),
            verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.md),
        ) {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(requestTitle(request), modifier = Modifier.weight(1f, fill = false), color = colors.onSurface, style = CardHeadlineText)
                    Text(relativeTime(request.createdAt), color = colors.onSurface.copy(alpha = 0.75f), style = CardLabelText)
                }
                Text(request.device, color = colors.onSurface.copy(alpha = 0.75f), style = CardSupportingText)
            }
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm),
            ) {
                RequestButton(stringResource(R.string.deny), onDeny, Modifier.weight(1f))
                RequestButton(stringResource(R.string.allow), onApprove, Modifier.weight(1f), outlined = true)
            }
        }
    }
}

/** What the request would allow: a sign-in, or one of the sensitive changes auth-v2 holds back. */
@Composable
private fun requestTitle(request: SignInRequest): String = when (request.action) {
    "authorize" -> request.detail?.let { stringResource(R.string.request_authorize, it) } ?: stringResource(R.string.request_authorize_unknown)
    "password_change" -> stringResource(R.string.request_password_change)
    "username_change" -> request.detail?.let { stringResource(R.string.request_username_change_to, it) } ?: stringResource(R.string.request_username_change)
    "account_delete" -> stringResource(R.string.request_account_delete)
    "require_app_disable" -> stringResource(R.string.request_require_app_disable)
    "recovery_phrase_create" -> stringResource(R.string.request_recovery_phrase_create)
    "recovery_phrase_remove" -> stringResource(R.string.request_recovery_phrase_remove)
    else -> stringResource(R.string.sign_in_request)
}

@Composable
private fun RequestButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, outlined: Boolean = false) {
    val haptic = LocalHapticFeedback.current
    Text(
        label,
        modifier = modifier
            .height(LocalDimensions.current.actionHeight)
            .clip(RoundedCornerShape(LocalDimensions.current.requestButtonRadius))
            .then(
                if (outlined) {
                    Modifier.border(
                        width = 2.dp,
                        color = androidx.compose.ui.graphics.Color.White,
                        shape = RoundedCornerShape(LocalDimensions.current.requestButtonRadius),
                    )
                } else {
                    Modifier.background(LocalAppColors.current.mutedSurface)
                },
            )
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        textAlign = TextAlign.Center,
        color = if (outlined) androidx.compose.ui.graphics.Color.White else LocalAppColors.current.onLightSurface,
        style = RequestMetaText,
    )
}

// Home's cards: the same inset on every side.
private val CardPadding = 16.dp
// The request card is a tighter, denser card than the account card below it.
private val RequestCardPadding = 10.dp
// Keeps the column off the screen edges on phones narrower than the bottom nav.
private val NavSideInset = 12.dp
private const val HistorySeenDelayMillis = 450L

private fun formatAccountCode(code: String) = "${code.take(3)}-${code.drop(3)}"

/**
 * The account's authenticator: this app holds the CNS account's TOTP key (see TwoFactorManager)
 * and shows its current code with the 30s timer row. Without a key here the card says why - 2FA
 * is off ("Set Up" turns it on with this app as the authenticator) or it lives on another device
 * ("Transfer" moves it here) - and hides the code and timer.
 */
@Composable
private fun AccountCard(
    state: TwoFactorState,
    code: String,
    spentCount: Int,
    isSettingUp: Boolean,
    onSetUp: () -> Unit,
    onTransfer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    // Sized to content, not a fixed height - the exported accountCardHeight token (137dp)
    // undershoots this card's real content (label + code row + timer squares + gaps ≈ 145dp),
    // which made AppCard's rounded-corner clip cut the bottom off the timer squares row.
    AppCard(modifier = modifier) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(CardPadding),
            verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.md),
        ) {
            // The card's name as a small label, so it never competes with what the card says.
            Text(stringResource(R.string.authenticator_default_label), color = colors.dialogSubtitle, style = CardLabelText)
            when (state) {
                is TwoFactorState.Active -> {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AnimatedAccountCode(code, color = colors.onSurface)
                        CopyCodeButton(code = code.filter(Char::isDigit))
                    }
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(17.dp)
                            .semantics { role = Role.Image },
                    ) {
                        TimerSquares(TimerSquareCount, spentCount)
                    }
                }
                TwoFactorState.Elsewhere -> {
                    CardMessage(stringResource(R.string.two_factor_elsewhere), stringResource(R.string.two_factor_elsewhere_hint))
                    CardWideButton(stringResource(R.string.transfer_2fa_here), isBusy = false, onClick = onTransfer)
                }
                TwoFactorState.NotSetUp, TwoFactorState.Unknown -> {
                    CardMessage(stringResource(R.string.two_factor_not_set_up), stringResource(R.string.two_factor_not_set_up_hint))
                    CardWideButton(
                        stringResource(R.string.set_up),
                        isBusy = isSettingUp,
                        enabled = state == TwoFactorState.NotSetUp,
                        onClick = onSetUp,
                    )
                }
            }
        }
    }
}

/** A card's headline with a smaller, muted line of explanation under it. */
@Composable
private fun CardMessage(headline: String, supporting: String) {
    val colors = LocalAppColors.current
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(headline, color = colors.onSurface, style = CardHeadlineText)
        Text(supporting, color = colors.dialogSubtitle, style = CardSupportingText)
    }
}

/** The card's main action: a white 48dp pill. */
@Composable
private fun CardWideButton(label: String, isBusy: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    val colors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(LocalDimensions.current.copyRadius))
            .background(if (enabled) androidx.compose.ui.graphics.Color.White else colors.mutedSurface.copy(alpha = 0.4f))
            .clickable(enabled = enabled && !isBusy) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        if (isBusy) {
            RefreshSpinner(phase = { BusySpinnerPhase }, size = 20.dp, color = colors.onLightSurface)
        } else {
            Text(
                label,
                color = colors.onLightSurface,
                style = RequestMetaText.copy(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
            )
        }
    }
}

private const val CopyConfirmationMillis = 1400L

@Composable
private fun CopyCodeButton(code: String) {
    val colors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copied by remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(LocalDimensions.current.copyRadius))
            .background(colors.surface)
            .clickable(enabled = !copied) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                copySensitive(context, code)
                copied = true
                scope.launch {
                    delay(CopyConfirmationMillis)
                    copied = false
                }
            }
            .padding(horizontal = 35.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = copied,
            transitionSpec = {
                (fadeIn(tween(180)) + scaleIn(initialScale = 0.7f, animationSpec = tween(180)))
                    .togetherWith(fadeOut(tween(140)) + scaleOut(targetScale = 0.7f, animationSpec = tween(140)))
            },
            label = "copy-button-content",
        ) { isCopied ->
            if (isCopied) {
                TokenIcon(TokenIconKind.Check, tint = colors.onSurface)
            } else {
                Text(
                    stringResource(R.string.copy),
                    color = colors.onSurface,
                    style = RequestMetaText.copy(fontSize = 16.sp, lineHeight = 20.sp),
                )
            }
        }
    }
}

/**
 * Copies a 2FA code flagged as sensitive, so Android hides it from the clipboard preview and
 * keyboard suggestions (and, from Android 13, from the "copied" overlay) like a password.
 */
private fun copySensitive(context: android.content.Context, code: String) {
    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java) ?: return
    val clip = android.content.ClipData.newPlainText(context.getString(R.string.authenticator_default_label), code)
    clip.description.extras = android.os.PersistableBundle().apply {
        // ClipDescription.EXTRA_IS_SENSITIVE (API 33); older releases read the same key.
        putBoolean("android.content.extra.IS_SENSITIVE", true)
    }
    clipboard.setPrimaryClip(clip)
}

@Preview(showBackground = true, widthDp = 390, heightDp = 874)
@Composable
private fun HomeScreenPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        HomeScreen(
            state = HomeUiState(data = sampleHomeData()),
            onDestinationSelected = {},
            onSettingsClick = {},
            onAccountClick = {},
            onOpenSetting = {},
            onRefresh = {},
            onRefreshHistory = {},
            onLoadMoreHistory = {},
            onHistorySeen = {},
            onApproveRequest = {},
            onDenyRequest = {},
            onSetUpTwoFactor = {},
            onTransferTwoFactor = {},
            onToggleService = {},
            onDisconnectService = {},
            onSetUpService = {},
            onApproveServiceLogin = {},
            onDenyServiceLogin = {},
            onDialogFieldChange = { _, _ -> },
            onDialogConfirm = {},
            onDialogDismiss = {},
        )
    }
}

@Composable
private fun AnimatedAccountCode(code: String, color: androidx.compose.ui.graphics.Color) {
    androidx.compose.foundation.layout.Row {
        code.forEachIndexed { index, char ->
            androidx.compose.animation.AnimatedContent(
                targetState = char,
                transitionSpec = {
                    (androidx.compose.animation.slideInVertically(
                        animationSpec = androidx.compose.animation.core.tween(320),
                    ) { height -> height } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(320)))
                        .togetherWith(
                            androidx.compose.animation.slideOutVertically(
                                animationSpec = androidx.compose.animation.core.tween(320),
                            ) { height -> -height } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(320)),
                        )
                },
                label = "account-code-char-$index",
            ) { targetChar ->
                // On top of the slide and fade, each digit blurs as it leaves and sharpens as it
                // lands (the blur follows how far through its own enter/exit it is).
                val blur by transition.animateFloat(
                    transitionSpec = { androidx.compose.animation.core.tween(320) },
                    label = "account-code-char-blur-$index",
                ) { phase -> if (phase == androidx.compose.animation.EnterExitState.Visible) 0f else 1f }
                Text(
                    targetChar.toString(),
                    modifier = Modifier.graphicsLayer {
                        val radius = blur * CodeBlurRadius.toPx()
                        renderEffect = if (radius > 0.1f) BlurEffect(radius, radius, TileMode.Decal) else null
                    },
                    color = color,
                    style = AccountCodeText,
                )
            }
        }
    }
}

private val CodeBlurRadius = 6.dp

private val TimerSquareAnim = androidx.compose.animation.core.tween<androidx.compose.ui.graphics.Color>(durationMillis = 90)

@Composable
private fun TimerSquares(count: Int, spentCount: Int) {
    // Squares are spent right-to-left; the leftmost (index 0) square is red and is the last
    // one spent each 30s cycle, at which point the whole row resets and starts again from the right.
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
      repeat(count) { index ->
        val isSpent = index >= count - spentCount
        val defaultColor = if (index == 0) LocalAppColors.current.accent else androidx.compose.ui.graphics.Color.White
        val targetColor = if (isSpent) LocalAppColors.current.timerSpent else defaultColor
        val color by animateColorAsState(targetColor, animationSpec = TimerSquareAnim, label = "timer-square-$index")
        // Shares the card's width, so the row always spans it, whatever the card's width.
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .weight(1f)
                .height(17.dp)
                .background(color),
        )
      }

    }
}

@Preview(name = "Home screen dark", showBackground = true, widthDp = 390, heightDp = 874, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeScreenDarkPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme(darkTheme = true) {
        HomeScreen(
            state = HomeUiState(data = sampleHomeData()),
            onDestinationSelected = {},
            onSettingsClick = {},
            onAccountClick = {},
            onOpenSetting = {},
            onRefresh = {},
            onRefreshHistory = {},
            onLoadMoreHistory = {},
            onHistorySeen = {},
            onApproveRequest = {},
            onDenyRequest = {},
            onSetUpTwoFactor = {},
            onTransferTwoFactor = {},
            onToggleService = {},
            onDisconnectService = {},
            onSetUpService = {},
            onApproveServiceLogin = {},
            onDenyServiceLogin = {},
            onDialogFieldChange = { _, _ -> },
            onDialogConfirm = {},
            onDialogDismiss = {},
        )
    }
}

@Preview(name = "Sign-in request card", showBackground = true, widthDp = 340, heightDp = 146)
@Composable
private fun SignInRequestCardPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        SignInRequestCard(sampleHomeData().signInRequest!!, {}, {})
    }
}

@Preview(name = "Account card", showBackground = true, widthDp = 340, heightDp = 145)
@Composable
private fun AccountCardPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        AccountCard(sampleHomeData().twoFactor, "123-456", 0, isSettingUp = false, onSetUp = {}, onTransfer = {})
    }
}

@Preview(name = "Account card, not set up", showBackground = true, widthDp = 340, heightDp = 145)
@Composable
private fun AccountCardNotSetUpPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        AccountCard(TwoFactorState.NotSetUp, "", 0, isSettingUp = false, onSetUp = {}, onTransfer = {})
    }
}

