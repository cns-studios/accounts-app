package com.cns.accounts.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cns.accounts.R
import com.cns.accounts.ui.components.HelpTooltipIcon
import com.cns.accounts.ui.components.TokenIcon
import com.cns.accounts.ui.components.TokenIconKind
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.LocalDimensions
import com.cns.accounts.ui.theme.ProfileNameText
import com.cns.accounts.ui.theme.RequestMetaText

// The pieces Your Account and the app Settings page are both built from.

/** A page title with the round back button before it. */
@Composable
internal fun SettingsHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(TokenIconKind.ChevronLeft, contentDescription = stringResource(R.string.back), onClick = onBack)
        Text(title, color = colors.onSurface, style = ProfileNameText)
    }
}

/** A 48dp circle around a 24dp glyph: the back button and the profile's edit button. */
@Composable
internal fun RoundIconButton(icon: TokenIconKind, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .size(RoundButtonSize)
            .clip(CircleShape)
            .background(colors.elevatedSurface)
            .clickable(onClickLabel = contentDescription, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        TokenIcon(icon, tint = colors.onSurface)
    }
}

private val RoundButtonSize = 48.dp

@Composable
internal fun ToggleRow(
    modifier: Modifier,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    helpContentDescription: String? = null,
    helpHint: String? = null,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = colors.onSurface, style = RequestMetaText)
            if (helpContentDescription != null && helpHint != null) {
                HelpTooltipIcon(contentDescription = helpContentDescription, hint = helpHint)
            }
        }
        SettingsToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// Toggle tokens (StateOn / StateOff): a 40x28 pill with a 24dp knob inset 2dp top/bottom and 3dp
// from its edge (13dp on the other side). On: white track, black knob, knob right. Off: gray
// (149,149,149) track, dark gray (76,76,76) knob, knob left.
private val ToggleTrackWidth = 40.dp
private val ToggleTrackHeight = 28.dp
private val ToggleThumbSize = 24.dp
private val ToggleThumbInset = 3.dp

@Composable
internal fun SettingsToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) ToggleTrackWidth - ToggleThumbSize - ToggleThumbInset else ToggleThumbInset,
        label = "toggle-thumb-offset",
    )
    val trackColor by animateColorAsState(if (checked) Color.White else colors.toggleOffTrack, label = "toggle-track-color")
    val thumbColor by animateColorAsState(if (checked) Color.Black else colors.toggleOffThumb, label = "toggle-thumb-color")
    Box(
        modifier = Modifier
            .size(ToggleTrackWidth, ToggleTrackHeight)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .toggleable(value = checked, role = Role.Switch) { newValue ->
                haptic.performHapticFeedback(if (newValue) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                onCheckedChange(newValue)
            },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = thumbOffset)
                .size(ToggleThumbSize)
                .clip(CircleShape)
                .background(thumbColor),
        )
    }
}

/** A full-height action button: white (primary) or the elevated surface with white text (secondary). */
@Composable
internal fun ActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, secondary: Boolean = false) {
    val colors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        modifier = modifier.height(LocalDimensions.current.actionHeight),
        shape = RoundedCornerShape(11.dp),
        colors = if (secondary) {
            ButtonDefaults.buttonColors(containerColor = colors.elevatedSurface, contentColor = Color.White)
        } else {
            ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
        },
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        // Two buttons share a row, so a long label shrinks to fit instead of being cut off.
        androidx.compose.foundation.text.BasicText(
            text,
            style = RequestMetaText.copy(color = LocalContentColor.current),
            maxLines = 1,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = RequestMetaText.fontSize),
        )
    }
}
