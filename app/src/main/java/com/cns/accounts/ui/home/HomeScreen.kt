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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import com.cns.accounts.R
import com.cns.accounts.data.Account
import com.cns.accounts.data.SignInRequest
import com.cns.accounts.data.Service
import com.cns.accounts.ui.components.AppCard
import com.cns.accounts.ui.components.HomeBottomNavigation
import com.cns.accounts.ui.components.ProfileHeader
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
    onApproveRequest: () -> Unit,
    onDenyRequest: () -> Unit,
    onCopyAccountCode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { androidx.compose.runtime.mutableStateOf("home") }
    var swipeProgress by remember { mutableFloatStateOf(0f) }
    var timerMillis by remember { mutableIntStateOf(0) }
    var accountCode by remember { androidx.compose.runtime.mutableStateOf(state.data.account.code) }
    var searchActive by remember { androidx.compose.runtime.mutableStateOf(false) }
    var searchQuery by remember { androidx.compose.runtime.mutableStateOf("") }
    val searchAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (searchActive) 1f else 0f,
        animationSpec = tween(280),
        label = "search-overlay-alpha",
    )
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        var totalMillis = 0
        while (true) {
            delay(100L)
            totalMillis += 100
            val newTimerMillis = totalMillis % 30_000
            if (newTimerMillis < timerMillis) {
                accountCode = com.cns.accounts.data.randomAccountCode()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            timerMillis = newTimerMillis
        }
    }
    val spacing = LocalSpacing.current
    val dimensions = LocalDimensions.current
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxSize()
            .background(LocalAppColors.current.background)
            .navigationBarsPadding(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = -swipeProgress * size.width
                    alpha = 1f - swipeProgress
                },
            contentPadding = PaddingValues(
                start = spacing.xl,
                end = spacing.xl,
                top = 62.dp,
                bottom = dimensions.navHeight + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item(key = "profile", contentType = "header") {
                ProfileHeader(state.data.profile, onSettingsClick)
            }
            item(key = "profile-request-gap", contentType = "spacer") {
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 24.dp))
            }
            item(key = "request", contentType = "request") {
                SignInRequestCard(state.data.signInRequest, onApproveRequest, onDenyRequest)
            }
            item(key = "request-account-gap", contentType = "spacer") {
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 17.dp))
            }
            item(key = "account", contentType = "account") {
                AccountCard(state.data.account, accountCode, timerMillis, onCopyAccountCode)
            }
            item(key = "account-services-gap", contentType = "spacer") {
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 17.dp))
            }
            item(key = "services", contentType = "services") {
                ServicesCard(state.data.services)
            }
        }
        com.cns.accounts.ui.history.HistoryScreen(
            state = com.cns.accounts.ui.history.HistoryUiState(),
            modifier = Modifier.graphicsLayer {
                translationX = (1f - swipeProgress) * size.width
                alpha = swipeProgress
            },
        )
        // search.tokens.json: an opaque full-screen overlay above Home/History while search is
        // active; closing it (the bar's X) just fades it away, revealing whichever tab was
        // showing underneath — Home or History are untouched, not navigated away from.
        if (searchActive || searchAlpha > 0.01f) {
            com.cns.accounts.ui.search.SearchScreen(
                query = searchQuery,
                modifier = Modifier.graphicsLayer { alpha = searchAlpha },
            )
        }
        HomeBottomNavigation(
            selectedTab = selectedTab,
            onTabSelected = { tab ->
                selectedTab = tab
                if (tab == "home") onDestinationSelected(tab)
            },
            onSwipeProgress = { swipeProgress = it },
            onUserClick = onSettingsClick,
            isSearchActive = searchActive,
            onSearchActiveChange = { searchActive = it },
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        )
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

@Composable
private fun AccountCard(account: Account, code: String, timerMillis: Int, onCopy: () -> Unit) {
    val colors = LocalAppColors.current
    // Sized to content, not a fixed height — the exported accountCardHeight token (137dp)
    // undershoots this card's real content (label + code row + timer squares + gaps ≈ 145dp),
    // which made AppCard's rounded-corner clip cut the bottom off the timer squares row.
    AppCard {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(LocalSpacing.current.md),
            verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.md),
        ) {
            Text(account.label, color = colors.onSurface, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedAccountCode(code, color = colors.onSurface)
                CopyCodeButton(code = code, onCopy = onCopy)
            }
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(17.dp)
                    .semantics { role = Role.Image },
            ) {
                TimerSquares(account.secretLength, timerMillis)
            }
        }

    }
}

private const val CopyConfirmationMillis = 1400L

@Composable
private fun CopyCodeButton(code: String, onCopy: () -> Unit) {
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
                onCopy()
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

@Composable
private fun ServicesCard(services: List<Service>) {
    val colors = LocalAppColors.current
    AppCard(modifier = Modifier.height(LocalDimensions.current.serviceCardHeight)) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(LocalSpacing.current.md),
            verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.lg),
        ) {
            Text(stringResource(R.string.services), color = colors.onSurface, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            services.forEach { service ->
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(service.name, color = colors.onSurface, style = RequestTitleText)
                        Text(service.status, color = colors.serviceAccent, style = RequestMetaText)
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
            state = HomeUiState(),
            onDestinationSelected = {},
            onSettingsClick = {},
            onApproveRequest = {},
            onDenyRequest = {},
            onCopyAccountCode = {},
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
private fun TimerSquares(count: Int, elapsedMillis: Int) {
    // Squares are spent right-to-left; the leftmost (index 0) square is red and is the last
    // one spent each 30s cycle, at which point the whole row resets and starts again from the right.
    val spentCount = ((elapsedMillis + 100) * count / 30_000f).toInt().coerceIn(0, count)
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
            state = HomeUiState(),
            onDestinationSelected = {},
            onSettingsClick = {},
            onApproveRequest = {},
            onDenyRequest = {},
            onCopyAccountCode = {},
        )
    }
}

@Preview(name = "Sign-in request card", showBackground = true, widthDp = 340, heightDp = 146)
@Composable
private fun SignInRequestCardPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        SignInRequestCard(HomeUiState().data.signInRequest, {}, {})
    }
}

@Preview(name = "Account card", showBackground = true, widthDp = 340, heightDp = 145)
@Composable
private fun AccountCardPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        val account = HomeUiState().data.account
        AccountCard(account, account.code, 0, {})
    }
}

@Preview(name = "Services card", showBackground = true, widthDp = 340, heightDp = 180)
@Composable
private fun ServicesCardPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        ServicesCard(HomeUiState().data.services)
    }
}
