package com.cns.accounts.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cns.accounts.ui.home.HomeScreen
import com.cns.accounts.ui.home.HomeViewModel

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val viewModel: HomeViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                state = state,
                onDestinationSelected = { destination ->
                    viewModel.selectDestination(destination)
                    if (destination != "home") navController.navigate(destination)
                },
                onSettingsClick = { },
                onApproveRequest = viewModel::approveRequest,
                onDenyRequest = viewModel::denyRequest,
                onCopyAccountCode = viewModel::copyAccountCode,
            )
        }
        composable("profile") { PlaceholderScreen("Profile") }
        composable("search") { PlaceholderScreen("Search") }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.material3.Text(title)
    }
}
