package com.cns.accounts.data

import androidx.compose.runtime.Immutable
import java.time.Instant

// history.json (Frame9/10/13/12/11): each activity card is title + relative time + a
// two-line description of what happened and where. Title/description come from [eventType]
// (see ui/history/ActivityLabels.kt); [subtitle] is the device auth-v2 inferred from the
// user agent, or the IP address when it couldn't.
@Immutable
data class HistoryEvent(
    val id: Long,
    val eventType: String,
    val createdAt: Instant?,
    val subtitle: String?,
    val data: Map<String, String> = emptyMap(),
)

fun sampleHistoryEvents(): List<HistoryEvent> = List(5) { index ->
    HistoryEvent(
        id = index.toLong(),
        eventType = "logout",
        createdAt = Instant.now().minusSeconds(22 * 3600),
        subtitle = "Linux · Firefox",
    )
}
