package com.attiqtherehman.kharcha

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.attiqtherehman.kharcha.applock.AppLockManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KharchaApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var appStartTasks: AppStartTasks

    @Inject
    lateinit var appLockManager: AppLockManager

    override fun onCreate() {
        super.onCreate()
        appLockManager.register()
        appStartTasks.register()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
