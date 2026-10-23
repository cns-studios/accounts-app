package com.cns.accounts.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cns.accounts.R
import com.cns.accounts.data.Service
import com.cns.accounts.data.ServiceLoginRequest
import com.cns.accounts.ui.components.AppCard
import com.cns.accounts.ui.components.TokenIcon
import com.cns.accounts.ui.components.TokenIconKind
import com.cns.accounts.ui.format.relativeTime
import com.cns.accounts.ui.theme.AccountCodeText
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.LocalDimensions
import com.cns.accounts.ui.theme.RequestMetaText
import com.cns.accounts.ui.theme.RequestTitleText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// TEMP_SERVICE_CARD_TOKENS: the Services card (#2D2D2D, 17dp radius, 14dp padding, 18dp gaps).
// - StateDefault: "Services" (20sp) over rows 5dp apart — service name (24sp medium) and a
//   chevron-right in a #1A1A1A circle (8dp padding).
// - StateAlert: a row with a pending login request gets a red time ("now") after its name.
// - StateApp (row tapped): the service name as the header with a chevron-left to go back, "Last
//   used" (20sp) over the date (24sp, "01.01.2027 - 00:00"), and a full-width 41dp button: red
//   (#C8102E) "Disconnect", or white "Set Up" for a service waiting to be set up.
// - StateAppAlert: instead, "Login request" + red time, "Device" / "Code" rows with monospace
//   values, and white Deny / Allow buttons side by side.
// A service that needs setting up (Sendly, until this app is a trusted Sendly device) shows an
// orange (#FFC700) dot after its name.
private val SetupDot = Color(0xFFFFC700)
private val LastUsedFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy - HH:mm")

@Composable
internal fun ServicesCard(
    services: List<Service>?,
    expandedServiceId: String?,
    onToggleService: (Service) -> Unit,
    onDisconnect: (Service) -> Unit,
    onSetUp: (Service) -> Unit,
    onApproveLogin: (Service) -> Unit,
    onDenyLogin: (Service) -> Unit,
) {
    val expanded = services?.firstOrNull { it.id == expandedServiceId }
    AppCard(modifier = Modifier.heightIn(min = LocalDimensions.current.serviceCardHeight)) {
        AnimatedContent(
            targetState = expanded,
            transitionSpec = {
                (fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120)))
                    .using(SizeTransform(clip = false))
            },
            contentKey = { it?.id },
            label = "services-card",
        ) { service ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (service == null) {
                    ServiceList(services, onToggleService)
                } else {
                    ServiceDetail(service, onToggleService, onDisconnect, onSetUp, onApproveLogin, onDenyLogin)
                }
            }
        }
    }
}

@Composable
private fun ServiceList(services: List<Service>?, onToggleService: (Service) -> Unit) {
    val colors = LocalAppColors.current
    Text(stringResource(R.string.services), color = colors.onSurface, style = RequestMetaText)
    if (services != null && services.isEmpty()) {
        Text(stringResource(R.string.no_connected_apps), color = colors.mutedText, style = RequestMetaText)
    }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        services.orEmpty().forEach { service ->
            ServiceRow(service, onClick = { onToggleService(service) })
        }
    }
}

@Composable
private fun ServiceRow(service: Service, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics { role = Role.Button },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
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
            val request = service.loginRequest
            when {
                request != null -> Text(relativeTime(request.createdAt), color = colors.serviceAccent, style = RequestMetaText)
                service.needsSetup -> SetupDot()
            }
        }
        ChevronButton(TokenIconKind.ChevronRight, contentDescription = null)
    }
}

@Composable
private fun SetupDot() {
    val label = stringResource(R.string.service_needs_setup)
    Box(
        modifier = Modifier
            .padding(start = 3.dp)
            .size(8.dp)
            .clip(CircleShape)
            .background(SetupDot)
            .semantics { contentDescription = label },
    )
}

@Composable
private fun ServiceDetail(
    service: Service,
    onToggleService: (Service) -> Unit,
    onDisconnect: (Service) -> Unit,
    onSetUp: (Service) -> Unit,
    onApproveLogin: (Service) -> Unit,
    onDenyLogin: (Service) -> Unit,
) {
    val colors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    val backLabel = stringResource(R.string.back)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            service.name,
            modifier = Modifier.weight(1f),
            color = colors.onSurface,
            style = RequestMetaText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ChevronButton(
            TokenIconKind.ChevronLeft,
            contentDescription = backLabel,
            modifier = Modifier
                .clip(CircleShape)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleService(service)
                },
        )
    }
    val request = service.loginRequest
    if (request != null) {
        LoginRequestDetail(request)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardButton(stringResource(R.string.deny), Color.White, Color.Black, Modifier.weight(1f)) { onDenyLogin(service) }
            CardButton(stringResource(R.string.allow), Color.White, Color.Black, Modifier.weight(1f)) { onApproveLogin(service) }
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(R.string.last_used), color = colors.onSurface, style = RequestMetaText)
            Text(
                service.lastUsed?.let(::formatLastUsed) ?: stringResource(R.string.last_used_unknown),
                color = colors.onSurface,
                style = RequestTitleText.copy(fontWeight = null),
            )
        }
        if (service.needsSetup) {
            CardButton(stringResource(R.string.set_up), Color.White, Color.Black, Modifier.fillMaxWidth()) { onSetUp(service) }
        } else {
            CardButton(stringResource(R.string.disconnect), colors.accent, Color.White, Modifier.fillMaxWidth()) { onDisconnect(service) }
        }
    }
}

@Composable
private fun LoginRequestDetail(request: ServiceLoginRequest) {
    val colors = LocalAppColors.current
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.login_request), color = colors.onSurface, style = RequestTitleText)
            Text(relativeTime(request.createdAt), color = colors.serviceAccent, style = RequestMetaText)
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            LabeledValue(stringResource(R.string.login_request_device), request.device)
            LabeledValue(stringResource(R.string.login_request_code), formatRequestCode(request.code))
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.onSurface, style = RequestMetaText)
        Text(
            value,
            modifier = Modifier.weight(1f),
            color = colors.onSurface,
            style = AccountCodeText.copy(fontSize = RequestMetaText.fontSize, lineHeight = RequestMetaText.lineHeight),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChevronButton(icon: TokenIconKind, contentDescription: String?, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(colors.surface)
            .padding(8.dp)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
    ) {
        TokenIcon(icon, tint = colors.onSurface)
    }
}

@Composable
private fun CardButton(label: String, container: Color, content: Color, modifier: Modifier, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .height(LocalDimensions.current.actionHeight)
            .clip(RoundedCornerShape(LocalDimensions.current.requestButtonRadius))
            .background(container)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = content, style = RequestMetaText)
    }
}

private fun formatLastUsed(instant: Instant): String = LastUsedFormat.format(instant.atZone(ZoneId.systemDefault()))

// Sendly's verification codes are 6 digits; shown grouped like the account code ("123-123").
private fun formatRequestCode(code: String): String = if (code.length == 6) "${code.take(3)}-${code.drop(3)}" else code

@Preview(name = "Services, list", showBackground = true, widthDp = 340)
@Composable
private fun ServicesCardListPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        ServicesCard(
            listOf(Service("sendly", "Sendly", Instant.now(), needsSetup = true), Service("ilc", "ILC", Instant.now())),
            expandedServiceId = null, {}, {}, {}, {}, {},
        )
    }
}

@Preview(name = "Services, tapped", showBackground = true, widthDp = 340)
@Composable
private fun ServicesCardDetailPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        ServicesCard(listOf(Service("ilc", "ILC", Instant.now())), expandedServiceId = "ilc", {}, {}, {}, {}, {})
    }
}

@Preview(name = "Services, login request", showBackground = true, widthDp = 340)
@Composable
private fun ServicesCardRequestPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        val request = ServiceLoginRequest("e1", "Zen on Linux", "123123", Instant.now())
        ServicesCard(listOf(Service("sendly", "Sendly", Instant.now(), loginRequest = request)), expandedServiceId = "sendly", {}, {}, {}, {}, {})
    }
}
