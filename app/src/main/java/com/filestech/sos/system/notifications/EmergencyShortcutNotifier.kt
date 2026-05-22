package com.filestech.sos.system.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.filestech.sos.R
import com.filestech.sos.system.receiver.EmergencyShortcutReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the persistent emergency shortcut notification visible on the lock screen.
 *
 * - IMPORTANCE_LOW / VISIBILITY_PUBLIC: always visible on lock screen, no sound.
 * - setOngoing(true): user cannot swipe away.
 * - setLocalOnly(true): not bridged to Wear.
 * - 3 actions: URGENCE (trigger SMS via BroadcastReceiver), 112 (ACTION_DIAL), 17 (ACTION_DIAL,
 *   conditional on `showPolice` parameter).
 * - Cancel in PanicDecoy: must be suppressed to avoid leaking emergency-mode presence under
 *   coercion. MainApplication.onCreate combine flow handles this.
 *
 * Port from SMS Tech EmergencyShortcutNotifier with SOS Tech package/strings.
 */
@Singleton
class EmergencyShortcutNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun post(context: Context, showPolice: Boolean = true) {
        val notif = buildNotification(context, showPolice)
        runCatching { nm.notify(NOTIF_ID, notif) }
            .onFailure { Timber.w(it, "EmergencyShortcutNotifier: post failed") }
    }

    fun cancel(context: Context) {
        runCatching { nm.cancel(NOTIF_ID) }
            .onFailure { Timber.w(it, "EmergencyShortcutNotifier: cancel failed") }
    }

    private fun buildNotification(context: Context, showPolice: Boolean): Notification {
        val urgenceIntent = Intent(context, EmergencyShortcutReceiver::class.java).apply {
            action = EmergencyShortcutReceiver.ACTION_TRIGGER_EMERGENCY
        }
        val urgencePi = PendingIntent.getBroadcast(
            context,
            RC_URGENCE,
            urgenceIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val dial112Intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val dial112Pi = PendingIntent.getActivity(
            context,
            RC_112,
            dial112Intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val dial17Intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:17")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val dial17Pi = PendingIntent.getActivity(
            context,
            RC_17,
            dial17Intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = Notification.Builder(context, NotificationChannelInitializer.CHANNEL_EMERGENCY_SHORTCUT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.notif_emergency_shortcut_title))
            .setContentText(context.getString(R.string.notif_emergency_shortcut_text))
            .setOngoing(true)
            .setLocalOnly(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(Notification.Action.Builder(
                null,
                context.getString(R.string.notif_emergency_shortcut_action_emergency),
                urgencePi,
            ).build())
            .addAction(Notification.Action.Builder(
                null,
                context.getString(R.string.notif_emergency_shortcut_action_112),
                dial112Pi,
            ).build())

        if (showPolice) {
            builder.addAction(Notification.Action.Builder(
                null,
                context.getString(R.string.notif_emergency_shortcut_action_17),
                dial17Pi,
            ).build())
        }

        return builder.build()
    }

    companion object {
        const val NOTIF_ID = 1001
        private const val RC_URGENCE = 2001
        private const val RC_112 = 2002
        private const val RC_17 = 2003
    }
}
