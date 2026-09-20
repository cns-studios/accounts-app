package com.cns.accounts.ui.home

import androidx.lifecycle.ViewModel
import com.cns.accounts.data.sampleHomeData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState(data = sampleHomeData()))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun selectDestination(destination: String) {
        _uiState.value = _uiState.value.copy(selectedDestination = destination)
    }

    fun approveRequest() = Unit
    fun denyRequest() = Unit
    fun copyAccountCode() = Unit
}
