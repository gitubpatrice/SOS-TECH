package com.filestech.sos

import com.filestech.sos.core.result.AppError
import com.filestech.sos.core.result.Outcome
import com.filestech.sos.data.local.datastore.AppSettings
import com.filestech.sos.domain.emergency.EmergencyCallBehavior
import com.filestech.sos.domain.recording.RecordingConfig
import com.filestech.sos.domain.recording.RECORDING_MAX_DURATION_MS
import com.filestech.sos.domain.siren.SIREN_MAX_DURATION_MS
import com.filestech.sos.domain.webhook.WEBHOOK_MAX_RETRIES
import com.filestech.sos.security.EmergencyCallHelper
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Scaffold guard-regression tests for SOS Tech v0.1.
 * Verifies that all security-critical defaults are conservative.
 * These tests MUST stay green across all future versions (never weaken a default).
 */
class AuditV001Test {

    @Test
    fun `default AppSettings all features are OFF`() {
        val s = AppSettings()
        assertThat(s.emergency.enabled).isFalse()
        assertThat(s.voice.enabled).isFalse()
        assertThat(s.cascade.enabled).isFalse()
        assertThat(s.emergency.sirenEnabled).isFalse()
        assertThat(s.liveGps.enabled).isFalse()
        assertThat(s.recording.enabled).isFalse()
        assertThat(s.webhook.enabled).isFalse()
    }

    @Test
    fun `default call behavior is HOLD_3S_DIRECT_CALL not TAP`() {
        val s = AppSettings()
        assertThat(s.security.emergencyCallBehavior).isEqualTo(EmergencyCallBehavior.HOLD_3S_DIRECT_CALL)
    }

    @Test
    fun `FLAG_SECURE is ON by default`() {
        val s = AppSettings()
        assertThat(s.security.flagSecure).isTrue()
    }

    @Test
    fun `recording requires legal disclaimer before enabling`() {
        val r = RecordingConfig()
        assertThat(r.enabled).isFalse()
        assertThat(r.userAcknowledgedLegalDisclaimer).isFalse()
    }

    @Test
    fun `emergency whitelist contains exactly 112 15 17 18`() {
        assertThat(EmergencyCallHelper.ALLOWED_NUMBERS).containsExactly("112", "15", "17", "18")
    }

    @Test
    fun `siren max duration is 5 minutes`() {
        assertThat(SIREN_MAX_DURATION_MS).isEqualTo(5 * 60 * 1_000L)
    }

    @Test
    fun `recording max duration is 30 seconds`() {
        assertThat(RECORDING_MAX_DURATION_MS).isEqualTo(30 * 1_000L)
    }

    @Test
    fun `webhook max retries is 3`() {
        assertThat(WEBHOOK_MAX_RETRIES).isEqualTo(3)
    }

    @Test
    fun `Outcome sealed type pattern matching is exhaustive`() {
        val success: Outcome<Int> = Outcome.Success(42)
        val failure: Outcome<Int> = Outcome.Failure(AppError.Unknown())

        val s = when (success) {
            is Outcome.Success -> success.value
            is Outcome.Failure -> -1
        }
        assertThat(s).isEqualTo(42)

        val f = when (failure) {
            is Outcome.Success -> failure.value
            is Outcome.Failure -> -1
        }
        assertThat(f).isEqualTo(-1)
    }

    @Test
    fun `AppError NotImplemented is distinct from Unknown`() {
        val ni = AppError.NotImplemented("webhook")
        val u = AppError.Unknown()
        assertThat(ni).isNotEqualTo(u)
        assertThat(ni).isInstanceOf(AppError.NotImplemented::class.java)
    }

    @Test
    fun `voice trigger default repetitionCount is 3 anti-false-positive`() {
        val s = AppSettings()
        assertThat(s.voice.repetitionCount).isEqualTo(3)
    }

    @Test
    fun `cascade noAnswerTimeout default is 10 seconds`() {
        val s = AppSettings()
        assertThat(s.cascade.noAnswerTimeoutMs).isEqualTo(10_000L)
    }

    @Test
    fun `webhook default does not include GPS`() {
        val s = AppSettings()
        assertThat(s.webhook.includeGps).isFalse()
    }
}
