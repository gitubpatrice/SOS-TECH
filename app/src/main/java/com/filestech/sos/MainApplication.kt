package com.filestech.sos

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.filestech.sos.core.logging.LineNumberDebugTree
import com.filestech.sos.core.logging.NoOpReleaseTree
import com.filestech.sos.di.ApplicationScope
import com.filestech.sos.system.notifications.NotificationChannelInitializer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class MainApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var notificationChannelInitializer: NotificationChannelInitializer

    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(
                if (BuildConfig.LOG_ENABLED) android.util.Log.DEBUG else android.util.Log.ERROR
            )
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.LOG_ENABLED) {
            Timber.plant(LineNumberDebugTree())
        } else {
            Timber.plant(NoOpReleaseTree())
        }
        notificationChannelInitializer.ensureDefaultChannels()
        Timber.d("MainApplication: SOS Tech v%s started", BuildConfig.VERSION_NAME)
    }
}
