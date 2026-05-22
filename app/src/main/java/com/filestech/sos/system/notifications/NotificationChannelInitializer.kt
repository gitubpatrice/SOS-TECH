package com.filestech.sos.system.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationChannelInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun ensureDefaultChannels() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Emergency shortcut — persistent lock-screen notification
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_EMERGENCY_SHORTCUT,
                context.getString(com.filestech.sos.R.string.channel_emergency_shortcut_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(com.filestech.sos.R.string.channel_emergency_shortcut_desc)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )

        // Voice keyword trigger — persistent foreground service
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_VOICE_TRIGGER,
                context.getString(com.filestech.sos.R.string.channel_voice_trigger_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(com.filestech.sos.R.string.channel_voice_trigger_desc)
            }
        )

        // Cascade dialer — foreground service
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CASCADE_DIALER,
                context.getString(com.filestech.sos.R.string.channel_cascade_dialer_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(com.filestech.sos.R.string.channel_cascade_dialer_desc)
            }
        )

        // Live GPS — foreground service
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_LIVE_GPS,
                context.getString(com.filestech.sos.R.string.channel_live_gps_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(com.filestech.sos.R.string.channel_live_gps_desc)
            }
        )

        Timber.d("NotificationChannelInitializer: all channels ensured")
    }

    companion object {
        const val CHANNEL_EMERGENCY_SHORTCUT = "sos_emergency_shortcut"
        const val CHANNEL_VOICE_TRIGGER = "sos_voice_trigger"
        const val CHANNEL_CASCADE_DIALER = "sos_cascade_dialer"
        const val CHANNEL_LIVE_GPS = "sos_live_gps"
    }
}
