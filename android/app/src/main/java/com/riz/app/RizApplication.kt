package com.riz.app

import android.app.Application
import com.riz.app.crypto.PasswordPolicy
import com.riz.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class RizApplication : Application() {
    lateinit var container: AppContainer
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val warmupStarted = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /**
     * Trigger background init AFTER the first frame is on screen. Running it earlier (in
     * onCreate) competes with Compose for CPU during the first-frame window and causes
     * Choreographer "Skipped frames" via UI-thread starvation.
     */
    fun startPostFrameWarmup() {
        if (!warmupStarted.compareAndSet(false, true)) return
        appScope.launch {
            PasswordPolicy.init(this@RizApplication)
            container.securityRepository.initializePool()
        }
    }
}
