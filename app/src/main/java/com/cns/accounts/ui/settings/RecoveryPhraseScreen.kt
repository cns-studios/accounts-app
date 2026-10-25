package com.cns.accounts.ui.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cns.accounts.R
import com.cns.accounts.ui.components.AppTextField
import com.cns.accounts.ui.components.ErrorBanner
import com.cns.accounts.ui.components.FieldKind
import com.cns.accounts.ui.components.SecureWindow
import com.cns.accounts.ui.components.rememberErrorShake
import com.cns.accounts.ui.theme.AccountCodeText
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.LocalSpacing
import com.cns.accounts.ui.theme.ProfileNameText
import com.cns.accounts.ui.theme.RequestCaptionText
import com.cns.accounts.ui.theme.RequestMetaText
import com.cns.accounts.ui.theme.SettingsValueText
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Your Account > Recovery Phrase. The window is secure (no screenshots, recordings or
 * recent-apps preview) for the whole page, since it shows the words.
 */
@Composable
fun RecoveryPhraseScreen(
    state: RecoveryPhraseUiState,
    onBack: () -> Unit,
    onPasswordChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onSubmitVerify: () -> Unit,
    onNewPhrase: () -> Unit,
    onRemove: () -> Unit,
    onReveal: () -> Unit,
    onSaveFile: (Uri, String) -> Unit,
    onSavedItChange: (Boolean) -> Unit,
    onContinueToConfirm: () -> Unit,
    onAnswerChange: (Int, String) -> Unit,
    onSubmitConfirm: () -> Unit,
    onStepBack: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    SecureWindow()
    val colors = LocalAppColors.current
    val spacing = LocalSpacing.current
    LaunchedEffect(state.finished) { if (state.finished) onBack() }
    BackHandler(enabled = !state.finished) { if (!onStepBack()) onBack() }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        SettingsHeader(
            title = stringResource(R.string.recovery_phrase),
            onBack = { if (!onStepBack()) onBack() },
            modifier = Modifier.padding(horizontal = spacing.xl, vertical = spacing.lg),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.xl, vertical = spacing.sm)
                .then(rememberErrorShake(state.error)),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state.step) {
                RecoveryPhraseStep.Overview -> OverviewStep(state, onNewPhrase, onRemove)
                RecoveryPhraseStep.Verify -> VerifyStep(state, onPasswordChange, onCodeChange, onSubmitVerify)
                RecoveryPhraseStep.Show -> ShowStep(state, onReveal, onSaveFile, onSavedItChange, onContinueToConfirm)
                RecoveryPhraseStep.Confirm -> ConfirmStep(state, onAnswerChange, onSubmitConfirm)
            }
        }
    }
}

@Composable
private fun Intro(title: String, body: String) {
    val colors = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = colors.onSurface, style = ProfileNameText)
        Text(body, color = colors.fieldPlaceholder, style = RequestCaptionText)
    }
}

@Composable
private fun StepError(state: RecoveryPhraseUiState) {
    ErrorBanner(state.error?.resolve(LocalContext.current), contentPadding = PaddingValues(bottom = 4.dp))
}

@Composable
private fun OverviewStep(state: RecoveryPhraseUiState, onNewPhrase: () -> Unit, onRemove: () -> Unit) {
    val date = state.createdAt?.let {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(it)
    }
    Intro(
        stringResource(R.string.recovery_phrase_on_title),
        date?.let { stringResource(R.string.recovery_phrase_on_body_date, it) } ?: stringResource(R.string.recovery_phrase_on_body),
    )
    StepError(state)
    ActionButton(stringResource(R.string.recovery_phrase_new), onClick = onNewPhrase, modifier = Modifier.fillMaxWidth())
    ActionButton(stringResource(R.string.recovery_phrase_remove), onClick = onRemove, modifier = Modifier.fillMaxWidth(), secondary = true)
}

@Composable
private fun VerifyStep(
    state: RecoveryPhraseUiState,
    onPasswordChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Intro(
        stringResource(
            when {
                state.removing -> R.string.recovery_phrase_remove_title
                state.enabled -> R.string.recovery_phrase_new_title
                else -> R.string.recovery_phrase_setup_title
            },
        ),
        stringResource(
            when {
                state.removing -> R.string.recovery_phrase_remove_body
                state.enabled -> R.string.recovery_phrase_new_body
                else -> R.string.recovery_phrase_setup_body
            },
        ),
    )
    AppTextField(
        value = state.password,
        onValueChange = onPasswordChange,
        placeholder = stringResource(R.string.password_placeholder),
        kind = FieldKind.Password,
        enabled = !state.isBusy,
        imeAction = if (state.askForCode) ImeAction.Next else ImeAction.Done,
        onImeAction = { if (!state.askForCode) onSubmit() },
    )
    if (state.askForCode) {
        AppTextField(
            value = state.code,
            onValueChange = onCodeChange,
            placeholder = stringResource(R.string.code_placeholder),
            kind = FieldKind.Code,
            enabled = !state.isBusy,
            imeAction = ImeAction.Done,
            onImeAction = onSubmit,
        )
    }
    StepError(state)
    ActionButton(
        if (state.isBusy) stringResource(R.string.please_wait) else stringResource(if (state.removing) R.string.recovery_phrase_remove else R.string.continue_label),
        onClick = { if (!state.isBusy) onSubmit() },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ShowStep(
    state: RecoveryPhraseUiState,
    onReveal: () -> Unit,
    onSaveFile: (Uri, String) -> Unit,
    onSavedItChange: (Boolean) -> Unit,
    onContinue: () -> Unit,
) {
    val colors = LocalAppColors.current
    val draft = state.draft ?: return
    // The same text file the web page downloads. The system picker decides where it goes (no
    // storage permission, no copy kept by the app).
    val created = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(java.time.Instant.now())
    val fileText = listOf(
        stringResource(R.string.recovery_phrase_file_heading, state.username),
        stringResource(R.string.recovery_phrase_file_created, created),
        "",
        draft.words.mapIndexed { i, word -> "${i + 1}. $word" }.joinToString("\n"),
        "",
        stringResource(R.string.recovery_phrase_file_warning),
    ).joinToString("\n", postfix = "\n")
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) onSaveFile(uri, fileText)
    }
    Intro(stringResource(R.string.recovery_phrase_show_title), stringResource(R.string.recovery_phrase_show_body))
    Text(stringResource(R.string.recovery_phrase_warning), color = colors.onSurface, style = RequestCaptionText)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(colors.elevatedSurface)
            .then(if (state.revealed) Modifier else Modifier.clickable(role = Role.Button, onClick = onReveal))
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (state.revealed) {
            // Two columns of six, numbered: the order matters.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                draft.words.chunked(2).forEachIndexed { row, pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEachIndexed { col, word ->
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Text("${row * 2 + col + 1}.", modifier = Modifier.padding(end = 8.dp), color = colors.fieldPlaceholder, style = SettingsValueText)
                                Text(word, color = colors.onSurface, style = AccountCodeText.copy(fontSize = RequestMetaText.fontSize, lineHeight = RequestMetaText.lineHeight))
                            }
                        }
                    }
                }
            }
        } else {
            Text(
                stringResource(R.string.recovery_phrase_tap_to_reveal),
                modifier = Modifier.padding(vertical = 48.dp),
                color = colors.onSurface,
                style = RequestMetaText,
                textAlign = TextAlign.Center,
            )
        }
    }
    if (state.revealed) {
        ActionButton(
            stringResource(R.string.recovery_phrase_save_file),
            onClick = { saveFile.launch("cns-recovery-phrase-${state.username}.txt") },
            modifier = Modifier.fillMaxWidth(),
            secondary = true,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = state.revealed, role = Role.Checkbox) { onSavedItChange(!state.savedIt) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = state.savedIt,
            onCheckedChange = onSavedItChange,
            enabled = state.revealed,
            colors = CheckboxDefaults.colors(checkedColor = Color.White, checkmarkColor = Color.Black, uncheckedColor = colors.fieldPlaceholder),
        )
        Text(stringResource(R.string.recovery_phrase_saved_it), color = colors.onSurface, style = RequestCaptionText)
    }
    ActionButton(
        stringResource(R.string.continue_label),
        onClick = { if (state.revealed && state.savedIt) onContinue() },
        modifier = Modifier.fillMaxWidth(),
        secondary = !(state.revealed && state.savedIt),
    )
}

@Composable
private fun ConfirmStep(state: RecoveryPhraseUiState, onAnswerChange: (Int, String) -> Unit, onSubmit: () -> Unit) {
    val draft = state.draft ?: return
    Intro(stringResource(R.string.recovery_phrase_confirm_title), stringResource(R.string.recovery_phrase_confirm_body))
    draft.confirmPositions.forEachIndexed { index, position ->
        val last = index == draft.confirmPositions.lastIndex
        AppTextField(
            value = state.answers.getOrElse(index) { "" },
            onValueChange = { onAnswerChange(index, it) },
            placeholder = stringResource(R.string.recovery_phrase_word_n, position),
            kind = FieldKind.PhraseWord,
            enabled = !state.isBusy,
            imeAction = if (last) ImeAction.Done else ImeAction.Next,
            onImeAction = { if (last) onSubmit() },
        )
    }
    StepError(state)
    ActionButton(
        if (state.isBusy) stringResource(R.string.please_wait) else stringResource(R.string.recovery_phrase_save),
        onClick = { if (!state.isBusy) onSubmit() },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Preview(showBackground = true, widthDp = 402, heightDp = 874)
@Composable
private fun RecoveryPhraseShowPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        RecoveryPhraseScreen(
            state = RecoveryPhraseUiState(
                step = RecoveryPhraseStep.Show,
                revealed = true,
                draft = com.cns.accounts.data.api.RecoveryPhraseDraft(
                    "token",
                    "legal winner thank year wave sausage worth useful legal winner thank yellow".split(" "),
                    listOf(2, 7, 11),
                ),
            ),
            onBack = {}, onPasswordChange = {}, onCodeChange = {}, onSubmitVerify = {}, onNewPhrase = {}, onRemove = {},
            onReveal = {}, onSaveFile = { _, _ -> }, onSavedItChange = {}, onContinueToConfirm = {}, onAnswerChange = { _, _ -> }, onSubmitConfirm = {},
            onStepBack = { false },
        )
    }
}
