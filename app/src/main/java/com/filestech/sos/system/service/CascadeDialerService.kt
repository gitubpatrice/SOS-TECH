package com.filestech.sos.system.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.filestech.sos.system.notifications.NotificationChannelInitializer
import timber.log.Timber

/**
 * ForegroundService for the automatic cascade dialer.
 *
 * Monitors call state via TelephonyManager.PhoneStateListener (requires READ_PHONE_STATE).
 * Calls contact #1; if no answer after [noAnswerTimeoutMs], hangs up and calls #2, etc.
 *
 * Declared in AndroidManifest with:
 *   - FOREGROUND_SERVICE type (generic — no specific type for telephony monitoring)
 *   - exported=false
 *
 * Implementation: v0.2+
 * PanicDecoy guard: must be applied before any outgoing call.
 */
class CascadeDialerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        // TODO v0.2: implement PhoneStateListener cascade logic
        Timber.d("CascadeDialerService: stub v0.1")
        stopSelf() // Stub: immediately stops since there is no real implementation yet
        return START_NOT_STICKY
    }

    private fun buildNotification(): android.app.Notification =
        android.app.Notification.Builder(this, NotificationChannelInitializer.CHANNEL_CASCADE_DIALER)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(getString(com.filestech.sos.R.string.cascade_dialer_notif_title))
            .setContentText(getString(com.filestech.sos.R.string.cascade_dialer_notif_text))
            .setOngoing(true)
            .build()

    companion object {
        private const val NOTIFICATION_ID = 1003

        fun start(context: Context) {
            context.startForegroundService(Intent(context, CascadeDialerService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CascadeDialerService::class.java))
        }
    }
}
