package com.filestech.sos

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.filestech.sos.core.logging.LineNumberDebugTree
import com.filestech.sos.core.logging.NoOpReleaseTree
import com.filestech.sos.data.local.datastore.SettingsRepository
import com.filestech.sos.di.ApplicationScope
import com.filestech.sos.security.AppLockManager
import com.filestech.sos.security.AutoLockObserver
import com.filestech.sos.system.notifications.EmergencyShortcutNotifier
import com.filestech.sos.system.notifications.NotificationChannelInitializer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class MainApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notificationChannelInitializer: NotificationChannelInitializer
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope
    @Inject lateinit var appLockManager: AppLockManager
    @Inject lateinit var autoLockObserver: AutoLockObserver
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var emergencyShortcutNotifier: EmergencyShortcutNotifier

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

        // Resolve app-lock initial state ASAP — fail-closed default is Locked until this runs.
        appScope.launch {
            appLockManager.resolveInitialState()
        }

        // Auto-lock on background via ProcessLifecycleOwner.
        autoLockObserver.register()

        // Emergency shortcut notification — post/cancel driven by settings + PanicDecoy state.
        appScope.launch {
            combine(settings.flow, appLockManager.state) { s, lockState ->
                Triple(
                    s.emergency.enabled,
                    s.emergency.shortcutNotifEnabled,
                    lockState is AppLockManager.LockState.PanicDecoy,
                )
            }.collect { (emergencyEnabled, shortcutEnabled, isPanic) ->
                if (emergencyEnabled && shortcutEnabled && !isPanic) {
                    emergencyShortcutNotifier.post(applicationContext)
                } else {
                    emergencyShortcutNotifier.cancel(applicationContext)
                }
            }
        }

        Timber.d("MainApplication: SOS Tech v%s started", BuildConfig.VERSION_NAME)
    }
}
