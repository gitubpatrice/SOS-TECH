package com.filestech.sos.data.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.filestech.sos.core.ext.redactPhone
import com.filestech.sos.core.result.AppError
import com.filestech.sos.core.result.Outcome
import com.filestech.sos.core.result.runCatchingOutcome
import com.filestech.sos.domain.model.PhoneAddress
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin, sync wrapper around Android's `SmsManager`. SOS Tech is **not** the default SMS app
 * (it neither needs to be nor wants to be — it just needs to dispatch SMS to trusted contacts).
 *
 * Responsibilities:
 *  - Validate `SEND_SMS` runtime permission.
 *  - Split the body via `divideMessage` and pick the correct dispatch method (single vs multi-part).
 *  - Wrap exceptions into typed `AppError.Telephony` outcomes.
 *  - **Never log the recipient number in clear** — only `redactPhone()` form. Body length is logged
 *    but content is not (privacy + side-channel hygiene).
 *
 * What it does **not** do:
 *  - Persist a copy to `content://sms` or to Room (out of scope for SOS Tech; we do not maintain
 *    a thread history).
 *  - Delivery / sent PendingIntents — `sendTextMessage` returns immediately after handoff to the
 *    radio stack. A future v0.3 enhancement can add per-message PendingIntent feedback if the UI
 *    needs to surface real-world delivery status.
 */
@Singleton
class SmsDispatcher @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun hasSendPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    /**
     * Dispatch a single SMS to one recipient. The body is automatically split into multi-part
     * if necessary. Returns `Outcome.Success(Unit)` on successful handoff to the radio stack.
     */
    fun dispatch(recipient: PhoneAddress, body: String): Outcome<Unit> {
        if (!hasSendPermission()) return Outcome.Failure(AppError.Permission(Manifest.permission.SEND_SMS))
        if (!recipient.isValidDispatchTarget) return Outcome.Failure(AppError.Validation("recipient_no_digits"))
        if (body.isBlank()) return Outcome.Failure(AppError.Validation("body_blank"))

        return runCatchingOutcome(
            block = {
                val mgr = obtainSmsManager()
                val parts = mgr.divideMessage(body)
                if (parts.size <= 1) {
                    mgr.sendTextMessage(recipient.raw, null, body, null, null)
                } else {
                    mgr.sendMultipartTextMessage(recipient.raw, null, parts, null, null)
                }
                Timber.i("SmsDispatcher: handed off to=%s parts=%d bodyLen=%d", recipient.raw.redactPhone(), parts.size, body.length)
            },
            errorMapper = { t ->
                Timber.w(t, "SmsDispatcher: send failed to=%s", recipient.raw.redactPhone())
                AppError.Telephony(reason = "sms_dispatch_failed", cause = t)
            },
        )
    }

    private fun obtainSmsManager(): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
}
