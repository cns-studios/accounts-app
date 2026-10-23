package com.cns.accounts.ui.home

import androidx.compose.runtime.Immutable
import com.cns.accounts.data.HomeData
import com.cns.accounts.data.Service
import com.cns.accounts.data.UserMessage
import com.cns.accounts.ui.history.HistoryUiState

@Immutable
data class HomeUiState(
    val data: HomeData = HomeData(),
    val history: HistoryUiState = HistoryUiState(),
    val isRefreshing: Boolean = false,
    val selectedDestination: String = "home",
    val dialog: HomeDialog? = null,
    val isSettingUpTwoFactor: Boolean = false,
)

/** The one dialog Home can show at a time; its field values live here so they survive recomposition. */
@Immutable
sealed interface HomeDialog {
    /** Move the account's 2FA onto this device: password + a code from the device that has it. */
    data class TransferTwoFactor(
        val password: String = "",
        val code: String = "",
        val isBusy: Boolean = false,
        val error: UserMessage? = null,
    ) : HomeDialog
    data class DisconnectApp(val service: Service, val isBusy: Boolean = false) : HomeDialog
}
