package com.filestech.sos.security

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.filestech.sos.core.ext.redactPhone
import com.filestech.sos.domain.emergency.EmergencyCallBehavior
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
 * controlled (DataStore contact configured by the user, not an Intent extra). Trusted-contact
 * calls share the same anti-spam cooldown as whitelisted emergency calls to prevent
 * pocket-dial chains immediately after an accidental 112 tap (v0.2 audit SEC-1).
 */
object EmergencyCallHelper {

    /** Strict whitelist of emergency numbers. Any other number is rejected at the call site. */
    val ALLOWED_NUMBERS: Set<String> = setOf("112", "15", "17", "18")

    /** Single-flight guard: prevents double-trigger during a concurrent call setup. */
    private val inFlight = AtomicBoolean(false)

    /** Monotonic timestamp of the last call attempt (anti-spam). Shared across whitelisted + trusted. */
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
        behavior: EmergencyCallBehavior,
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
            if (isInCooldown()) {
                Timber.w("EmergencyCallHelper: anti-spam cooldown active, ignoring call to %s", number)
                return
            }
            lastCallMonoMs.set(SystemClock.elapsedRealtime())

            when (behavior) {
                EmergencyCallBehavior.DIALER_ONLY -> openDialer(context, number)
                EmergencyCallBehavior.HOLD_3S_DIRECT_CALL,
                EmergencyCallBehavior.TAP_DIRECT_CALL -> {
                    if (hasCallPermission(context)) {
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
     * Subject to the same single-flight + anti-spam cooldown as emergency calls (v0.2 audit SEC-1).
     */
    fun placeTrustedContactCall(context: Context, phoneNumber: String) {
        if (!inFlight.compareAndSet(false, true)) {
            Timber.w("EmergencyCallHelper: call already in-flight (trusted contact)")
            return
        }
        try {
            if (isInCooldown()) {
                Timber.w("EmergencyCallHelper: anti-spam cooldown active (trusted contact %s)", phoneNumber.redactPhone())
                return
            }
            lastCallMonoMs.set(SystemClock.elapsedRealtime())

            if (hasCallPermission(context)) {
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
        }.onFailure { Timber.e(it, "EmergencyCallHelper: failed to open dialer for %s", number.redactPhone()) }
    }

    private fun executeDirectCall(context: Context, number: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure { Timber.e(it, "EmergencyCallHelper: direct call failed for %s, falling back to dialer", number.redactPhone()) }
    }

    private fun isInCooldown(): Boolean {
        val nowMono = SystemClock.elapsedRealtime()
        val lastMono = lastCallMonoMs.get()
        return lastMono > 0L && nowMono - lastMono < CALL_ANTI_SPAM_MS
    }

    private fun hasCallPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
}
