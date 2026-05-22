package com.filestech.sos.system.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.filestech.sos.di.ApplicationScope
import com.filestech.sos.data.local.datastore.SettingsRepository
import com.filestech.sos.security.AppLockManager
import com.filestech.sos.system.notifications.EmergencyShortcutNotifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

/**
 * Receives BOOT_COMPLETED to:
 *  1. Drift recovery for monotonicLastTriggeredAt — after a reboot, the monotonic clock resets
 *     to 0. If monotonicLastTriggeredAt > elapsedRealtime(), the cooldown stamp is invalid
 *     (it was recorded before the reboot). Reset to 0L to prevent a perpetually-active cooldown
 *     that can never expire (same pattern as SMS Tech v1.10.0 SEC-11).
 *  2. Re-post the emergency shortcut notification if emergency mode is still enabled.
 *  3. Trigger AppLockManager.ensureResolved() so the app-lock state is ready on cold process start.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var appLockManager: AppLockManager
    @Inject lateinit var notifier: EmergencyShortcutNotifier
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Timber.d("BootReceiver: BOOT_COMPLETED received")
        val pending = goAsync()
        appScope.launch {
            try {
                withTimeoutOrNull(3_000L) {
                    appLockManager.ensureResolved()

                    val s = settings.flow.first()
                    val now = SystemClock.elapsedRealtime()

                    // Drift recovery: monotonic stamp from before reboot cannot be valid anymore.
                    if (s.emergency.monotonicLastTriggeredAt > now) {
                        Timber.d("BootReceiver: drift detected (mono=%d > elapsed=%d) — resetting",
                            s.emergency.monotonicLastTriggeredAt, now)
                        settings.update { it.copy(emergency = it.emergency.copy(monotonicLastTriggeredAt = 0L)) }
                    }

                    val isPanic = appLockManager.state.value is AppLockManager.LockState.PanicDecoy
                    if (s.emergency.enabled && s.emergency.shortcutNotifEnabled && !isPanic) {
                        notifier.post(context, showPolice = true)
                        Timber.d("BootReceiver: emergency shortcut notif reposted")
                    } else {
                        notifier.cancel(context)
                    }
                } ?: Timber.w("BootReceiver: timed out after 3 s")
            } catch (e: Exception) {
                Timber.e(e, "BootReceiver: error during boot handling")
            } finally {
                pending.finish()
            }
        }
    }
}
