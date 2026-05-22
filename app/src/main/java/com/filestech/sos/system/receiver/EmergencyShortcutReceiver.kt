package com.filestech.sos.system.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.filestech.sos.di.ApplicationScope
import com.filestech.sos.domain.emergency.PanicGuard
import com.filestech.sos.domain.usecase.TriggerEmergencyUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Handles emergency shortcut actions fired from the persistent notification (lock-screen).
 * exported=false — only reachable via explicit Intent from this process.
 *
 * ACTION_TRIGGER_EMERGENCY: triggers the full SMS emergency flow via TriggerEmergencyUseCase.
 * PanicGuard check is delegated to the use case itself — receiver is a thin dispatch layer.
 */
@AndroidEntryPoint
class EmergencyShortcutReceiver : BroadcastReceiver() {

    @Inject lateinit var triggerUseCase: TriggerEmergencyUseCase
    @Inject lateinit var panicGuard: PanicGuard
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TRIGGER_EMERGENCY -> {
                if (panicGuard.isPanicActive()) {
                    Timber.d("EmergencyShortcutReceiver: PanicDecoy active — suppressing trigger")
                    return
                }
                val pending = goAsync()
                appScope.launch {
                    try {
                        triggerUseCase()
                        Timber.d("EmergencyShortcutReceiver: emergency trigger complete")
                    } catch (e: Exception) {
                        Timber.e(e, "EmergencyShortcutReceiver: trigger failed")
                    } finally {
                        pending.finish()
                    }
                }
            }
            else -> Timber.w("EmergencyShortcutReceiver: unknown action %s", intent.action)
        }
    }

    companion object {
        const val ACTION_TRIGGER_EMERGENCY = "com.filestech.sos.ACTION_TRIGGER_EMERGENCY"
    }
}
