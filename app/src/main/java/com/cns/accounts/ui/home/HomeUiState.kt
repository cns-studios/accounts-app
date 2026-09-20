package com.cns.accounts.ui.home

import androidx.compose.runtime.Immutable
import com.cns.accounts.data.HomeData

@Immutable
data class HomeUiState(
    val data: HomeData = HomeData(),
    val selectedDestination: String = "home",
)
