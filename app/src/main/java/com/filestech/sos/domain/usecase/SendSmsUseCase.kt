package com.filestech.sos.domain.usecase

import com.filestech.sos.core.result.AppError
import com.filestech.sos.core.result.Outcome
import com.filestech.sos.data.sms.SmsDispatcher
import com.filestech.sos.di.IoDispatcher
import com.filestech.sos.domain.model.PhoneAddress
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Domain-level wrapper around [SmsDispatcher]. Sends one SMS per recipient and aggregates
 * the per-recipient outcomes.
 *
 * Semantics:
 *  - `Outcome.Success(sentCount)` is returned when **at least one** recipient was handed off
 *    successfully. The `sentCount` is the number of successful dispatches; the caller can
 *    derive `failedCount = recipients.size - sentCount`.
 *  - `Outcome.Failure(AppError.Telephony)` is returned when ALL dispatches failed.
 *  - `Outcome.Failure(AppError.Validation)` is returned when the input itself is invalid
 *    (empty recipient list, blank body) — no dispatch is attempted.
 *
 * Each dispatch is **independent**: a single failed contact does not abort the rest. This is
 * the safest emergency policy — one bad number must not silence the whole alert.
 */
class SendSmsUseCase @Inject constructor(
    private val dispatcher: SmsDispatcher,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend operator fun invoke(recipients: List<PhoneAddress>, body: String): Outcome<Int> =
        withContext(io) {
            if (recipients.isEmpty()) return@withContext Outcome.Failure(AppError.Validation("no_recipients"))
            if (body.isBlank()) return@withContext Outcome.Failure(AppError.Validation("body_blank"))

            var sent = 0
            for (r in recipients) {
                when (dispatcher.dispatch(r, body)) {
                    is Outcome.Success -> sent++
                    is Outcome.Failure -> Unit // intentionally continue — one failure must not abort the alert
                }
            }
            if (sent == 0) Outcome.Failure(AppError.Telephony("all_dispatches_failed"))
            else Outcome.Success(sent)
        }
}
