package com.filestech.sos.system.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * Receives BOOT_COMPLETED to re-post the emergency shortcut notification if enabled.
 * Also handles drift recovery for monotonic cooldown after reboot
 * (same pattern as SMS Tech v1.10.0 SEC-11).
 *
 * Implementation: v0.2+ (needs SettingsRepository + ApplicationScope injection via goAsync).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // TODO v0.2: repost emergency shortcut notif + drift recovery
        Timber.d("BootReceiver: BOOT_COMPLETED (stub v0.1)")
    }
}
