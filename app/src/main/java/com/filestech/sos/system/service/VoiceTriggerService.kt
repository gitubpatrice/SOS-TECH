package com.filestech.sos.system.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.filestech.sos.system.notifications.NotificationChannelInitializer
import timber.log.Timber

/**
 * ForegroundService for continuous voice keyword monitoring.
 *
 * Declared in AndroidManifest with:
 *   - FOREGROUND_SERVICE_MICROPHONE type
 *   - exported=false
 *
 * Implementation: Vosk keyword spotter (v0.2+).
 * Prerequisites: user must have downloaded the Vosk model via SAF setup wizard.
 *
 * PanicDecoy guard: service checks AppLockManager state on each keyword match;
 * if PanicDecoy is active, the match is silently dropped to avoid leaking
 * the presence of the voice trigger feature under coercion.
 */
class VoiceTriggerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // TODO v0.2: initialize Vosk engine here
        Timber.d("VoiceTriggerService: onCreate (stub v0.1)")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        // TODO v0.2: start Vosk recognition loop
        Timber.d("VoiceTriggerService: onStartCommand (stub v0.1)")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // TODO v0.2: stop Vosk engine, release AudioRecord
        Timber.d("VoiceTriggerService: onDestroy")
    }

    private fun buildNotification(): android.app.Notification =
        android.app.Notification.Builder(this, NotificationChannelInitializer.CHANNEL_VOICE_TRIGGER)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(com.filestech.sos.R.string.voice_trigger_notif_title))
            .setContentText(getString(com.filestech.sos.R.string.voice_trigger_notif_text))
            .setOngoing(true)
            .build()

    companion object {
        private const val NOTIFICATION_ID = 1002

        fun start(context: Context) {
            context.startForegroundService(Intent(context, VoiceTriggerService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VoiceTriggerService::class.java))
        }
    }
}
