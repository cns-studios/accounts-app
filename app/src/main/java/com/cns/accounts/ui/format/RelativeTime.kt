package com.cns.accounts.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.cns.accounts.R
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** "now", "5m ago", "22h ago", "3d ago", then a short date - the design's compact time labels. */
@Composable
fun relativeTime(instant: Instant?, now: Instant = Instant.now()): String {
    if (instant == null) return ""
    val elapsed = Duration.between(instant, now)
    return when {
        elapsed.toMinutes() < 1 -> stringResource(R.string.relative_now)
        elapsed.toHours() < 1 -> stringResource(R.string.relative_minutes, elapsed.toMinutes())
        elapsed.toDays() < 1 -> stringResource(R.string.relative_hours, elapsed.toHours())
        elapsed.toDays() < 7 -> stringResource(R.string.relative_days, elapsed.toDays())
        else -> ShortDate.format(instant.atZone(ZoneId.systemDefault()))
    }
}

private val ShortDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
