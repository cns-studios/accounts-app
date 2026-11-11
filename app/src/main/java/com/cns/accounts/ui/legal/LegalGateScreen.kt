package com.cns.accounts.ui.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cns.accounts.R
import com.cns.accounts.data.api.LegalDocument
import com.cns.accounts.data.api.LegalState
import com.cns.accounts.ui.components.OnboardingGlow
import com.cns.accounts.ui.components.PillButton
import com.cns.accounts.ui.theme.CNSAccountsTheme
import com.cns.accounts.ui.theme.CardHeadlineText
import com.cns.accounts.ui.theme.CardSupportingText
import com.cns.accounts.ui.theme.DialogTitleText
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.LocalRadii
import com.cns.accounts.ui.theme.RequestCaptionText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * What the account still has to confirm about the terms of service and the privacy policy.
 *
 * While [state] is blocking (new terms are in effect) this is the whole app: accept, or leave
 * by logging out or deleting the account. Otherwise it is a notice the user confirms or puts
 * off with [onLater]: a privacy policy update is information, and announced terms can be
 * accepted ahead of time.
 */
@Composable
fun LegalGateScreen(
    state: LegalState,
    language: String,
    isBusy: Boolean,
    onRead: (LegalDocument) -> Unit,
    onConfirm: () -> Unit,
    onLater: () -> Unit,
    onLogOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val material = state.documents.any { it.material }
    var declining by rememberSaveable { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        OnboardingGlow(modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(if (state.blocking) R.string.legal_gate_title else R.string.legal_notice_title),
                    color = colors.onSurface,
                    style = DialogTitleText,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(
                        when {
                            state.blocking -> R.string.legal_gate_subtitle
                            material -> R.string.legal_notice_subtitle_material
                            else -> R.string.legal_notice_subtitle
                        },
                    ),
                    modifier = Modifier.widthIn(max = 320.dp),
                    color = colors.dialogSubtitle,
                    style = CardSupportingText,
                    textAlign = TextAlign.Center,
                )
            }
            state.documents.forEach { document ->
                DocumentCard(document, language, onRead = { onRead(document) })
            }
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (declining) {
                    Text(stringResource(R.string.legal_decline_text), color = colors.dialogSubtitle, style = RequestCaptionText)
                    PillButton(label = stringResource(R.string.legal_back), secondary = true, onClick = { declining = false })
                    PillButton(label = stringResource(R.string.log_out), secondary = true, onClick = onLogOut)
                    PillButton(label = stringResource(R.string.delete_account_dialog_title), secondary = true, onClick = onDeleteAccount)
                } else {
                    PillButton(
                        label = stringResource(
                            when {
                                state.blocking -> R.string.legal_accept
                                material -> R.string.legal_accept_now
                                else -> R.string.legal_got_it
                            },
                        ),
                        isBusy = isBusy,
                        onClick = onConfirm,
                    )
                    if (state.blocking) {
                        PillButton(label = stringResource(R.string.legal_decline), secondary = true, onClick = { declining = true })
                    } else {
                        PillButton(label = stringResource(R.string.legal_later), secondary = true, onClick = onLater)
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentCard(document: LegalDocument, language: String, onRead: () -> Unit) {
    val colors = LocalAppColors.current
    val dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
    val version = runCatching { LocalDate.parse(document.version).format(dates) }.getOrDefault(document.version)
    val summaries = document.summariesIn(language)
    Column(
        modifier = Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(LocalRadii.current.card))
            .background(colors.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(if (document.kind == "terms") R.string.legal_doc_terms else R.string.legal_doc_privacy),
                modifier = Modifier.weight(1f),
                color = colors.onSurface,
                style = CardHeadlineText,
            )
            Text(
                stringResource(R.string.legal_read),
                modifier = Modifier.clickable(role = Role.Button, onClick = onRead),
                color = colors.onSurface,
                style = RequestCaptionText.copy(textDecoration = TextDecoration.Underline),
            )
        }
        val effective = document.effectiveAt?.let { stringResource(R.string.legal_effective_from, it.atOffset(ZoneOffset.UTC).toLocalDate().format(dates)) }
        Text(
            listOfNotNull(stringResource(R.string.legal_version, version), effective).joinToString(" · "),
            color = colors.dialogSubtitle,
            style = RequestCaptionText,
        )
        if (summaries.isEmpty()) {
            Text(stringResource(R.string.legal_summary_fallback), color = colors.onSurface, style = CardSupportingText)
        } else {
            summaries.forEach { Text(it, color = colors.onSurface, style = CardSupportingText) }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 874)
@Composable
private fun LegalGateScreenPreview() {
    val terms = LegalDocument("terms", "2026-12-01", material = true, blocking = true, effectiveAt = null, summaries = listOf(mapOf("en" to "Section 7 now covers paid apps.")))
    val privacy = LegalDocument("privacy", "2026-11-11", material = false, blocking = false, effectiveAt = Instant.parse("2026-12-01T00:00:00Z"), summaries = emptyList())
    CNSAccountsTheme {
        LegalGateScreen(LegalState(true, listOf(terms, privacy)), "en", isBusy = false, onRead = {}, onConfirm = {}, onLater = {}, onLogOut = {}, onDeleteAccount = {})
    }
}
