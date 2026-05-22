package com.filestech.sos.security

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.ContextCompat
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Handles all emergency calls with strict whitelist + anti-pocket-dial + single-flight guard.
 *
 * Port and extension of SMS Tech v1.14.1 EmergencyCallHelper.
 * Adds TAP_DIRECT_CALL mode (available in SOS Tech as dedicated emergency app;
 * user accepts the pocket-dial risk explicitly).
 *
 * Whitelist: only 112, 15, 17, 18 are accepted for emergency routing.
 * Trusted contacts are handled via [placeTrustedContactCall] — no whitelist, source is
 * controlled (DataStore contact configured by the user, not an Intent extra).
 */
object EmergencyCallHelper {

    /** Strict whitelist of emergency numbers. Any other number is rejected at the call site. */
    val ALLOWED_NUMBERS: Set<String> = setOf("112", "15", "17", "18")

    /** Single-flight guard: prevents double-trigger during a concurrent call setup. */
    private val inFlight = AtomicBoolean(false)

    /** Monotonic timestamp of the last call attempt (anti-spam). */
    private val lastCallMonoMs = AtomicLong(0L)

    /** Minimum milliseconds between two direct call attempts. */
    private const val CALL_ANTI_SPAM_MS = 5_000L

    /**
     * Place a call to a whitelisted emergency number.
     *
     * @param number Must be in [ALLOWED_NUMBERS], otherwise rejected.
     * @param behavior How the call is initiated.
     */
    fun placeEmergencyCall(
        context: Context,
        number: String,
        behavior: com.filestech.sos.domain.emergency.EmergencyCallBehavior,
    ) {
        if (number !in ALLOWED_NUMBERS) {
            Timber.w("EmergencyCallHelper: rejected non-whitelisted number '%s'", number)
            return
        }
        if (!inFlight.compareAndSet(false, true)) {
            Timber.w("EmergencyCallHelper: call already in-flight, ignoring duplicate")
            return
        }
        try {
            val nowMono = SystemClock.elapsedRealtime()
            val lastMono = lastCallMonoMs.get()
            if (lastMono > 0L && nowMono - lastMono < CALL_ANTI_SPAM_MS) {
                Timber.w("EmergencyCallHelper: anti-spam cooldown active, ignoring call to %s", number)
                return
            }
            lastCallMonoMs.set(nowMono)

            when (behavior) {
                com.filestech.sos.domain.emergency.EmergencyCallBehavior.DIALER_ONLY -> openDialer(context, number)
                com.filestech.sos.domain.emergency.EmergencyCallBehavior.HOLD_3S_DIRECT_CALL,
                com.filestech.sos.domain.emergency.EmergencyCallBehavior.TAP_DIRECT_CALL -> {
                    if (ContextCompat.checkSelfPermission(
                            context, android.Manifest.permission.CALL_PHONE
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        executeDirectCall(context, number)
                    } else {
                        Timber.w("EmergencyCallHelper: CALL_PHONE not granted, falling back to dialer for %s", number)
                        openDialer(context, number)
                    }
                }
            }
        } finally {
            inFlight.set(false)
        }
    }

    /**
     * Place a call to a trusted contact. No whitelist check — source is controlled (DataStore).
     * Still subject to single-flight + anti-spam guards.
     */
    fun placeTrustedContactCall(context: Context, phoneNumber: String) {
        if (!inFlight.compareAndSet(false, true)) {
            Timber.w("EmergencyCallHelper: call already in-flight (trusted contact)")
            return
        }
        try {
            if (ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.CALL_PHONE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                executeDirectCall(context, phoneNumber)
            } else {
                openDialer(context, phoneNumber)
            }
        } finally {
            inFlight.set(false)
        }
    }

    /** Opens the system dialer pre-filled. No CALL_PHONE permission required. */
    fun openDialer(context: Context, number: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure { Timber.e(it, "EmergencyCallHelper: failed to open dialer for %s", number) }
    }

    private fun executeDirectCall(context: Context, number: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure { Timber.e(it, "EmergencyCallHelper: direct call failed for %s, falling back to dialer", number) }
    }
}
