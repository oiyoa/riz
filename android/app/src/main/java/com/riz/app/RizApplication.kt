package com.riz.app

import android.app.Application
import com.riz.app.di.AppContainer

class RizApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Begin precomputing derived keys if a password is already stored
        container.securityRepository.initializePool()
    }
}
