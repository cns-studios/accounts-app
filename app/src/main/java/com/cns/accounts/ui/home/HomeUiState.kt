package com.cns.accounts.ui.home

import androidx.compose.runtime.Immutable
import com.cns.accounts.data.HomeData
import com.cns.accounts.data.Service
import com.cns.accounts.ui.history.HistoryUiState

@Immutable
data class HomeUiState(
    val data: HomeData = HomeData(),
    val history: HistoryUiState = HistoryUiState(),
    val isRefreshing: Boolean = false,
    val selectedDestination: String = "home",
    val dialog: HomeDialog? = null,
)

/** The one dialog Home can show at a time; its field values live here so they survive recomposition. */
@Immutable
sealed interface HomeDialog {
    data class AddAuthenticator(val key: String = "", val label: String = "", val invalid: Boolean = false) : HomeDialog
    data object RemoveAuthenticator : HomeDialog
    data class DisconnectApp(val service: Service, val isBusy: Boolean = false) : HomeDialog
}
