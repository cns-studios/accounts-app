package com.cns.accounts

import android.app.Application
import android.content.Context
import com.cns.accounts.data.AppLanguage

class CnsApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
