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
import androidx.compose.foundation.layout.heightIn
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import com.cns.accounts.R
import com.cns.accounts.data.Account
import com.cns.accounts.data.SearchResultKind
import com.cns.accounts.data.SignInRequest
import com.cns.accounts.data.Service
import com.cns.accounts.data.TimerSquareCount
import com.cns.accounts.data.authenticator.Totp
import com.cns.accounts.data.sampleHomeData
import com.cns.accounts.ui.components.ActionDialog
import com.cns.accounts.ui.components.DialogField
import com.cns.accounts.ui.format.relativeTime
import com.cns.accounts.ui.components.AppCard
import com.cns.accounts.ui.components.FLING_VELOCITY_THRESHOLD
import com.cns.accounts.ui.components.HomeBottomNavigation
import com.cns.accounts.ui.components.NothingPullToRefreshBox
import com.cns.accounts.ui.components.ProfileHeader
import com.cns.accounts.ui.components.TabSettleSpring
import com.cns.accounts.ui.components.TokenIcon
import com.cns.accounts.ui.components.TokenIconKind
import com.cns.accounts.ui.theme.AccountCodeText
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.LocalDimensions
import com.cns.accounts.ui.theme.LocalSpacing
import com.cns.accounts.ui.theme.LocalRadii
import com.cns.accounts.ui.theme.RequestCaptionText
import com.cns.accounts.ui.theme.RequestMetaText
import com.cns.accounts.ui.theme.RequestTitleText

@Composable
fun HomeScreen(
    state: HomeUiState,
    onDestinationSelected: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onRefresh: () -> Unit,
    onRefreshHistory: () -> Unit,
    onLoadMoreHistory: () -> Unit,
    onApproveRequest: () -> Unit,
    onDenyRequest: () -> Unit,
    onAddAuthenticator: () -> Unit,
    onRemoveAuthenticator: () -> Unit,
    onServiceClick: (Service) -> Unit,
    onDialogFieldChange: (Int, String) -> Unit,
    onDialogConfirm: () -> Unit,
    onDialogDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { androidx.compose.runtime.mutableStateOf("home") }
    // Home/history swipe: a drag on EITHER the segmented nav pill or the page background below
    // drives this same liveSwipeProgress/isSwipeDragging pair — see onSwipeDragStarted/
    // onSwipeSettle — so it doesn't matter which surface the user actually touches, and
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
    // than on every 100ms tick — the ticks themselves still run every 100ms internally (for code
    // rotation + exact timing), they just don't all trigger recomposition of the account card.
    var spentCount by remember { mutableIntStateOf(0) }
    val account = state.data.account
    val secretBytes = remember(account?.secret) { account?.secret?.let(Totp::decodeBase32) }
    var accountCode by remember(secretBytes) {
        androidx.compose.runtime.mutableStateOf(secretBytes?.let { formatAccountCode(Totp.code(it, System.currentTimeMillis())) } ?: PlaceholderAccountCode)
    }
    var searchActive by remember { androidx.compose.runtime.mutableStateOf(false) }
    var searchQuery by remember { androidx.compose.runtime.mutableStateOf("") }
    val searchAlpha = animateFloatAsState(
        targetValue = if (searchActive) 1f else 0f,
        animationSpec = tween(280),
        label = "search-overlay-alpha",
    )
    // Only the visible/hidden flip is read during composition; the per-frame alpha itself is
    // read in graphicsLayer, so the fade doesn't recompose the whole screen every frame.
    val searchOverlayShown by remember { derivedStateOf { searchAlpha.value > 0.01f } }
    val haptic = LocalHapticFeedback.current
    val secretLength = account?.secretLength ?: TimerSquareCount
    // TOTP periods are aligned to wall-clock 30s boundaries, so the timer squares are too: the
    // code flips exactly when the row resets, matching any other authenticator app.
    LaunchedEffect(secretBytes, secretLength) {
        var lastPeriod = System.currentTimeMillis() / Totp.PeriodMillis
        while (true) {
            delay(100L)
            val now = System.currentTimeMillis()
            val period = now / Totp.PeriodMillis
            if (period != lastPeriod) {
                lastPeriod = period
                if (secretBytes != null) {
                    accountCode = formatAccountCode(Totp.code(secretBytes, now))
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
            val cycleMillis = now % Totp.PeriodMillis
            val newSpentCount = ((cycleMillis + 100) * secretLength / Totp.PeriodMillis.toFloat()).toInt().coerceIn(0, secretLength)
            if (newSpentCount != spentCount) {
                spentCount = newSpentCount
            }
        }
    }
    val spacing = LocalSpacing.current
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
                    alpha = 1f - progress
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
                            .padding(horizontal = spacing.xl),
                        contentAlignment = Alignment.Center,
                    ) {
                        ProfileHeader(state.data.profile, onSettingsClick)
                    }
                }
                item(key = "profile-request-gap", contentType = "spacer") {
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 24.dp))
                }
                // Only shown while a sign-in waits for approval — which auth-v2 can't produce yet
                // (see SignInRequests), so for now the account card follows the header directly.
                val signInRequest = state.data.signInRequest
                if (signInRequest != null) {
                    item(key = "request", contentType = "request") {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.xl),
                            contentAlignment = Alignment.Center,
                        ) {
                            SignInRequestCard(signInRequest, onApproveRequest, onDenyRequest)
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
                            .padding(horizontal = spacing.xl),
                        contentAlignment = Alignment.Center,
                    ) {
                        AccountCard(account, accountCode, spentCount, onAdd = onAddAuthenticator, onLongPress = onRemoveAuthenticator)
                    }
                }
                item(key = "account-services-gap", contentType = "spacer") {
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 17.dp))
                }
                item(key = "services", contentType = "services") {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.xl),
                        contentAlignment = Alignment.Center,
                    ) {
                        ServicesCard(state.data.services, onServiceClick)
                    }
                }
            }
        }
        com.cns.accounts.ui.history.HistoryScreen(
            state = state.history,
            onRefresh = onRefreshHistory,
            onLoadMore = onLoadMoreHistory,
            modifier = Modifier.graphicsLayer {
                val progress = swipeProgress()
                translationX = (1f - progress) * size.width
                alpha = progress
            },
        )
        // search.tokens.json: an opaque full-screen overlay above Home/History while search is
        // active; closing it (the bar's X) just fades it away, revealing whichever tab was
        // showing underneath — Home or History are untouched, not navigated away from.
        if (searchActive || searchOverlayShown) {
            val searchResults = com.cns.accounts.ui.search.rememberSearchResults(searchQuery, state.history.events)
            com.cns.accounts.ui.search.SearchScreen(
                query = searchQuery,
                results = searchResults,
                onResultClick = { result ->
                    searchActive = false
                    searchQuery = ""
                    when (result.kind) {
                        SearchResultKind.Activity -> selectTab("history")
                        else -> onSettingsClick()
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
            onUserClick = onSettingsClick,
            isSearchActive = searchActive,
            onSearchActiveChange = { searchActive = it },
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .padding(bottom = dimensions.navBottomGap),
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
        is HomeDialog.AddAuthenticator -> ActionDialog(
            title = stringResource(R.string.add_authenticator_title),
            subtitle = stringResource(R.string.add_authenticator_subtitle),
            primaryButtonLabel = stringResource(R.string.add),
            fields = listOf(
                DialogField(stringResource(R.string.authenticator_key_placeholder), dialog.key),
                DialogField(stringResource(R.string.authenticator_label_placeholder), dialog.label),
            ),
            onFieldChange = onFieldChange,
            errorText = if (dialog.invalid) stringResource(R.string.authenticator_invalid) else null,
            onDismissRequest = onDismiss,
            onPrimaryAction = onConfirm,
        )
        HomeDialog.RemoveAuthenticator -> ActionDialog(
            title = stringResource(R.string.remove_authenticator_title),
            subtitle = stringResource(R.string.remove_authenticator_subtitle),
            primaryButtonLabel = stringResource(R.string.remove),
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
        null -> Unit
    }
}

@Composable
private fun SignInRequestCard(
    request: SignInRequest,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    val colors = LocalAppColors.current
    // Sized to content (no fixed card height) so the bottom gap below the buttons is exactly
    // the padding value below, matching the left/right inset instead of leftover centering slack.
    AppCard(
        color = colors.accent,
        modifier = Modifier.semantics { role = Role.Button },
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = LocalSpacing.current.sm,
                    end = LocalSpacing.current.sm,
                    top = LocalSpacing.current.md,
                    bottom = LocalSpacing.current.sm,
                ),
            verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.lg),
        ) {
            androidx.compose.foundation.layout.Column {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LocalSpacing.current.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.sign_in_request), color = colors.onSurface, style = RequestTitleText)
                    Text(request.timeLabel, color = colors.onSurface.copy(alpha = 0.7f), style = RequestCaptionText)
                }
                Text(request.device, modifier = Modifier.padding(horizontal = LocalSpacing.current.sm), color = colors.onSurface.copy(alpha = 0.7f), style = RequestCaptionText)
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

private const val PlaceholderAccountCode = "\u2022\u2022\u2022-\u2022\u2022\u2022"

private fun formatAccountCode(code: String) = "${code.take(3)}-${code.drop(3)}"

/**
 * The standalone authenticator: the current TOTP code for the key stored on this device (see
 * AuthenticatorStore). With no key yet it shows a placeholder and an "Add" button; a long press
 * on a configured card offers to remove the key.
 */
@Composable
private fun AccountCard(account: Account?, code: String, spentCount: Int, onAdd: () -> Unit, onLongPress: () -> Unit) {
    val colors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    // Sized to content, not a fixed height — the exported accountCardHeight token (137dp)
    // undershoots this card's real content (label + code row + timer squares + gaps ≈ 145dp),
    // which made AppCard's rounded-corner clip cut the bottom off the timer squares row.
    AppCard(
        modifier = if (account == null) Modifier else Modifier
            .clip(RoundedCornerShape(LocalRadii.current.card))
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                },
            ),
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(LocalSpacing.current.md),
            verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.md),
        ) {
            Text(
                account?.label ?: stringResource(R.string.authenticator_default_label),
                color = colors.onSurface,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedAccountCode(code, color = if (account == null) colors.mutedText else colors.onSurface)
                if (account == null) {
                    AddKeyButton(onClick = onAdd)
                } else {
                    CopyCodeButton(code = code.filter(Char::isDigit))
                }
            }
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(17.dp)
                    .semantics { role = Role.Image },
            ) {
                TimerSquares(account?.secretLength ?: TimerSquareCount, spentCount)
            }
        }

    }
}

@Composable
private fun AddKeyButton(onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(LocalDimensions.current.copyRadius))
            .background(colors.background)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 35.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.add),
            color = colors.onSurface,
            style = RequestMetaText.copy(fontSize = 16.sp, lineHeight = 20.sp),
        )
    }
}

private const val CopyConfirmationMillis = 1400L

@Composable
private fun CopyCodeButton(code: String) {
    val colors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var copied by remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(LocalDimensions.current.copyRadius))
            .background(colors.background)
            .clickable(enabled = !copied) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                clipboard.setText(AnnotatedString(code))
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
 * Connected apps (auth-v2 `/api/me/connected-apps`): each row is the app plus when it last
 * used the account; the chevron opens the disconnect dialog. Grows with the list — the exported
 * serviceCardHeight (110dp) only fits the design's single row, so it is a minimum, not a size.
 */
@Composable
private fun ServicesCard(services: List<Service>?, onServiceClick: (Service) -> Unit) {
    val colors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    AppCard(modifier = Modifier.heightIn(min = LocalDimensions.current.serviceCardHeight)) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(LocalSpacing.current.md),
            verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.lg),
        ) {
            Text(stringResource(R.string.services), color = colors.onSurface, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            if (services != null && services.isEmpty()) {
                Text(stringResource(R.string.no_connected_apps), color = colors.mutedText, style = RequestMetaText)
            }
            services.orEmpty().forEach { service ->
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(LocalRadii.current.pill))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onServiceClick(service)
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.weight(1f, fill = false),
                        horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            service.name,
                            modifier = Modifier.weight(1f, fill = false),
                            color = colors.onSurface,
                            style = RequestTitleText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(relativeTime(service.lastUsed), color = colors.serviceAccent, style = RequestMetaText)
                    }

                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(LocalRadii.current.pill))
                            .background(colors.surface)
                            .padding(8.dp),
                    ) {
                        TokenIcon(TokenIconKind.ChevronRight, tint = colors.onSurface)
                    }
                }
            }

        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 874)
@Composable
private fun HomeScreenPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        HomeScreen(
            state = HomeUiState(data = sampleHomeData()),
            onDestinationSelected = {},
            onSettingsClick = {},
            onRefresh = {},
            onRefreshHistory = {},
            onLoadMoreHistory = {},
            onApproveRequest = {},
            onDenyRequest = {},
            onAddAuthenticator = {},
            onRemoveAuthenticator = {},
            onServiceClick = {},
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
                Text(targetChar.toString(), color = color, style = AccountCodeText)
            }
        }
    }
}

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
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(17.dp)
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
            onRefresh = {},
            onRefreshHistory = {},
            onLoadMoreHistory = {},
            onApproveRequest = {},
            onDenyRequest = {},
            onAddAuthenticator = {},
            onRemoveAuthenticator = {},
            onServiceClick = {},
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
        AccountCard(sampleHomeData().account, "123-456", 0, onAdd = {}, onLongPress = {})
    }
}

@Preview(name = "Services card", showBackground = true, widthDp = 340, heightDp = 180)
@Composable
private fun ServicesCardPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        ServicesCard(sampleHomeData().services, onServiceClick = {})
    }
}
