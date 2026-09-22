package com.cns.accounts.data

import androidx.compose.runtime.Immutable

// history.json (Frame9/10/13/12/11): each activity card is title + relative time + a
// two-line description of what happened and where.
@Immutable
data class HistoryEvent(
    val title: String,
    val timeLabel: String,
    val description: String,
    val subtitle: String,
)

fun sampleHistoryEvents(): List<HistoryEvent> = List(5) {
    HistoryEvent(
        title = "Signed out",
        timeLabel = "22h ago",
        description = "Logged out of an account or session",
        subtitle = "Zen on Linux",
    )
}
