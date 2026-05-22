package com.filestech.sos.domain.usecase

import android.os.SystemClock
import com.filestech.sos.core.result.Outcome
import com.filestech.sos.data.local.datastore.SettingsRepository
import com.filestech.sos.data.location.LocationResolver
import com.filestech.sos.di.IoDispatcher
import com.filestech.sos.domain.contact.EmergencyContactRepository
import com.filestech.sos.domain.emergency.EmergencyMessageRenderer
import com.filestech.sos.domain.emergency.PanicGuard
import com.filestech.sos.domain.model.PhoneAddress
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Orchestrates the emergency SMS trigger end-to-end.
 *
 * Flow (in order, fail-fast on each guard):
 *  1. [PanicGuard]   — abort silently (return `Result.PanicSuppressed`) if duress mode active.
 *  2. Cooldown       — re-check wall-clock AND `SystemClock.elapsedRealtime` against
 *                      `AppSettings.emergency.{lastTriggeredAt, monotonicLastTriggeredAt}` so an
 *                      attacker with root can't bypass the anti-spam window by manipulating
 *                      the wall clock (cf. SMS Tech v1.10 SEC-11).
 *  3. Contacts       — load from [EmergencyContactRepository]. If empty → `Result.NoContacts`.
 *  4. Location       — if `includeLocation` is true, attempt a single fix via [LocationResolver]
 *                      (8 s timeout). Failure to resolve is non-fatal: SMS goes out with the
 *                      `(position non disponible)` fallback in the body.
 *  5. Render body    — via [com.filestech.sos.domain.emergency.EmergencyTemplate] from settings.
 *                      Blank body → `Result.EmptyBody` (corrupted template).
 *  6. Dispatch       — per-contact via [SendSmsUseCase]. Each contact is independent: one
 *                      failed dispatch does not abort the rest.
 *  7. Cooldown stamp — wall+monotonic `lastTriggeredAt` persisted to settings on every trigger
 *                      attempt that reached dispatch (regardless of sent/failed counts).
 *
 * **Non-goals** for v0.2:
 *  - Cascade auto-call (separate `CascadeDialer` contract, stub).
 *  - Siren, flash, live GPS worker, recording, webhook — all stubs.
 *  - Calling each contact in priority order — only `cascadePriority`-ordered SMS in v0.2.
 *    Call cascade is wired in v0.3 via the real `CascadeDialer`.
 *
 * Logging rules:
 *  - Phone numbers are **never** logged in clear.
 *  - Lat/lon are **never** logged (only "hadLocation=true/false").
 *  - Body content is **never** logged (only `bodyLen=N`).
 */
class TriggerEmergencyUseCase @Inject constructor(
    private val sendSms: SendSmsUseCase,
    private val contacts: EmergencyContactRepository,
    private val settings: SettingsRepository,
    private val panicGuard: PanicGuard,
    private val locationResolver: LocationResolver,
    private val messageRenderer: EmergencyMessageRenderer,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend operator fun invoke(): Result = withContext(io) {
        if (panicGuard.isPanicActive()) {
            Timber.i("TriggerEmergencyUseCase: PanicGuard active — suppressing")
            return@withContext Result.PanicSuppressed
        }

        val snapshot = settings.flow.first()
        val emergency = snapshot.emergency

        // Defense-in-depth: even if the UI failed to gray out the trigger, re-check cooldown.
        val wallNow = System.currentTimeMillis()
        val monoNow = SystemClock.elapsedRealtime()
        val wallLast = emergency.lastTriggeredAt
        val monoLast = emergency.monotonicLastTriggeredAt
        val window = emergency.antiSpamWindowMs
        val inCooldown = (monoLast > 0L && (monoNow - monoLast) < window) ||
            (wallLast > 0L && (wallNow - wallLast) < window)
        if (inCooldown) {
            Timber.w("TriggerEmergencyUseCase: cooldown active — refusing")
            return@withContext Result.Cooldown(remainingMs = window - minOf(monoNow - monoLast, wallNow - wallLast))
        }

        val list = contacts.getAll()
        if (list.isEmpty()) {
            Timber.w("TriggerEmergencyUseCase: no contacts configured")
            return@withContext Result.NoContacts
        }

        val locationUrl: String? = if (emergency.includeLocation) {
            runCatching { locationResolver.getCurrentLocation() }
                .onFailure { Timber.w(it, "TriggerEmergencyUseCase: location resolve threw") }
                .getOrNull()
                ?.let { loc -> formatMapsUrl(loc.latitude, loc.longitude) }
        } else null
        val hadLocation = locationUrl != null

        val body = messageRenderer.render(emergency.template, locationUrl).trim()
        if (body.isBlank()) {
            Timber.e("TriggerEmergencyUseCase: rendered body is blank — template=%s", emergency.template.name)
            return@withContext Result.EmptyBody
        }

        // Order by cascadePriority (ascending; 0 = "not in cascade" but still messaged in v0.2).
        val ordered = list.sortedWith(compareBy({ it.cascadePriority }, { it.addedAtMs }))
        val recipients = ordered.map { PhoneAddress.of(it.phoneNumber) }
            .filter { it.isValidDispatchTarget }

        if (recipients.isEmpty()) {
            Timber.w("TriggerEmergencyUseCase: all contacts have unroutable numbers")
            return@withContext Result.NoContacts
        }

        Timber.i(
            "TriggerEmergencyUseCase: TRIGGER — recipients=%d template=%s hadLocation=%s bodyLen=%d",
            recipients.size, emergency.template.name, hadLocation, body.length,
        )

        val outcome = sendSms(recipients, body)
        val sent = when (outcome) {
            is Outcome.Success -> outcome.value
            is Outcome.Failure -> 0
        }
        val failed = recipients.size - sent

        // Stamp cooldown wall + mono (both, anti clock-manipulation) regardless of sent count —
        // even 0 successful sends mean we attempted, so the cooldown applies.
        settings.update { s ->
            s.copy(
                emergency = s.emergency.copy(
                    lastTriggeredAt = wallNow,
                    monotonicLastTriggeredAt = monoNow,
                ),
            )
        }

        Timber.i("TriggerEmergencyUseCase: done sent=%d failed=%d hadLocation=%s", sent, failed, hadLocation)
        return@withContext Result.Triggered(sent = sent, failed = failed, hadLocation = hadLocation)
    }

    private fun formatMapsUrl(lat: Double, lng: Double): String =
        // Universal Maps URL — works without Google Play Services on the recipient side.
        // 5 decimals ≈ 1 m precision, sufficient for emergency, keeps the SMS in 1 GSM-7 segment.
        "https://maps.google.com/?q=%.5f,%.5f".format(lat, lng)

    sealed interface Result {
        /** Duress mode active — trigger suppressed silently. */
        data object PanicSuppressed : Result

        /** Anti-spam cooldown window not elapsed. */
        data class Cooldown(val remainingMs: Long) : Result

        /** No contacts configured (or all unroutable). */
        data object NoContacts : Result

        /** Rendered template produced a blank body (corrupted template). */
        data object EmptyBody : Result

        /**
         * Trigger attempted. [sent] dispatches succeeded; [failed] = configured contacts minus sent.
         * [hadLocation] = whether the SMS body carried a Maps URL (false → "position non disponible").
         */
        data class Triggered(val sent: Int, val failed: Int, val hadLocation: Boolean) : Result
    }
}
