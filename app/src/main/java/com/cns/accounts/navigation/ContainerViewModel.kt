package com.cns.accounts.navigation

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cns.accounts.AppContainer
import com.cns.accounts.CnsApplication

/** `viewModel()` for ViewModels built from the app's [AppContainer]. */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(crossinline create: (AppContainer, Application) -> VM): VM {
    val application = LocalContext.current.applicationContext as CnsApplication
    return viewModel(factory = viewModelFactory { initializer { create(application.container, application) } })
}
