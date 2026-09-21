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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.animateColorAsState
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val showingHistory = selectedTab == "history"
    var timerSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        var totalSeconds = 0
        while (true) {
            delay(1000L)
            totalSeconds += 1
            timerSeconds = totalSeconds % 30
        }
    }
    val spacing = LocalSpacing.current
    val dimensions = LocalDimensions.current
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxSize()
            .background(LocalAppColors.current.background),
    ) {
        if (showingHistory) {
            HistorySurface()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = spacing.xl,
                    end = spacing.xl,
                    top = 62.dp,
                    bottom = dimensions.navHeight + 19.dp,
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
                    AccountCard(state.data.account, timerSeconds, onCopyAccountCode)
                }
                item(key = "account-services-gap", contentType = "spacer") {
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 17.dp))
                }
                item(key = "services", contentType = "services") {
                    ServicesCard(state.data.services)
                }
            }
        }
        HomeBottomNavigation(
            selectedTab = selectedTab,
            onTabSelected = { tab ->
                selectedTab = tab
                if (tab == "home") onDestinationSelected(tab)
            },
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .padding(bottom = 19.dp),
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
    AppCard(
        color = colors.accent,
        modifier = Modifier
            .height(154.dp)
            .semantics { role = Role.Button },
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = LocalSpacing.current.sm,
                    end = LocalSpacing.current.sm,
                    top = LocalSpacing.current.md,
                    bottom = LocalSpacing.current.sm
                ),
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LocalSpacing.current.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.sign_in_request), color = colors.onSurface, style = RequestTitleText)
                Text(request.timeLabel, color = colors.onSurface, style = RequestMetaText)
            }
            Text(request.device, modifier = Modifier.padding(horizontal = LocalSpacing.current.sm), color = colors.onSurface, style = RequestMetaText)
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(LocalSpacing.current.lg))
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm),
            ) {
                RequestButton(stringResource(R.string.deny), onDeny, Modifier.weight(1f))
                RequestButton(stringResource(R.string.allow), onApprove, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RequestButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        label,
        modifier = modifier
            .height(LocalDimensions.current.actionHeight)
            .clip(RoundedCornerShape(LocalDimensions.current.requestButtonRadius))
            .background(LocalAppColors.current.mutedSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        textAlign = TextAlign.Center,
        color = LocalAppColors.current.onLightSurface,
        style = RequestMetaText,
    )
}

@Composable
private fun AccountCard(account: Account, timerSeconds: Int, onCopy: () -> Unit) {
    val colors = LocalAppColors.current
    AppCard(modifier = Modifier.height(173.dp)) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(LocalSpacing.current.md),
            verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.md),
        ) {
            Text(account.label, color = colors.onSurface, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(account.code, color = colors.onSurface, style = AccountCodeText)
                Text(
                    stringResource(R.string.copy),
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(LocalDimensions.current.copyRadius))
                        .background(colors.background)
                        .clickable(onClick = onCopy)
                        .padding(horizontal = 35.dp, vertical = 13.dp),
                    color = colors.onSurface,
                    style = RequestMetaText.copy(fontSize = 16.sp),
                )
            }
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(17.dp)
                    .semantics { role = Role.Image },
            ) {
                TimerSquares(account.secretLength, timerSeconds)
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
private fun TimerSquares(count: Int, elapsedSeconds: Int) {
    val grayCount = ((count - 1) * elapsedSeconds / 30f).toInt()
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      repeat(count) { index ->
        val isGray = index >= count - grayCount
        val targetColor = when {
            index == 0 -> LocalAppColors.current.accent
            isGray -> LocalAppColors.current.mutedSurface
            else -> androidx.compose.ui.graphics.Color.White
        }
        val color by animateColorAsState(targetColor, label = "timer-square-$index")
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(17.dp)
                .weight(1f)
                .background(color),
        )
      }

    }
}

@Composable
private fun HistorySurface() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize().background(LocalAppColors.current.background),
        contentAlignment = Alignment.Center,
    ) {
        Text("History", color = LocalAppColors.current.onBackground)
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

@Preview(name = "Sign-in request card", showBackground = true, widthDp = 340, heightDp = 154)
@Composable
private fun SignInRequestCardPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        SignInRequestCard(HomeUiState().data.signInRequest, {}, {})
    }
}

@Preview(name = "Account card", showBackground = true, widthDp = 340, heightDp = 173)
@Composable
private fun AccountCardPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        AccountCard(HomeUiState().data.account, 0, {})
    }
}

@Preview(name = "Services card", showBackground = true, widthDp = 340, heightDp = 180)
@Composable
private fun ServicesCardPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        ServicesCard(HomeUiState().data.services)
    }
}
