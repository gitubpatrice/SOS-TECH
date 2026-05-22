package com.filestech.sos

import com.filestech.sos.core.ext.normalizePhone
import com.filestech.sos.core.ext.redactPhone
import com.filestech.sos.domain.emergency.EmergencyTemplate
import com.filestech.sos.domain.model.PhoneAddress
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * v0.2 guard-regression tests for the emergency trigger orchestration foundations.
 *
 * Scope (pure JVM, no Robolectric):
 *  - Phone normalization invariants
 *  - PhoneAddress dispatchability rules
 *  - EmergencyTemplate has the expected enum surface
 *  - Log redaction never leaks digits beyond the prefix and suffix
 *
 * Orchestration tests (TriggerEmergencyUseCase end-to-end with mocked deps) require Android
 * `Context` for the message renderer (R.string). Those land under `androidTest` in v0.3 when
 * the instrumented test scaffolding is wired.
 */
class AuditV020Test {

    // === Phone normalization ===

    @Test
    fun `normalizePhone keeps leading plus and digits only`() {
        assertThat("+33 6 12 34 56 78".normalizePhone()).isEqualTo("+33612345678")
        assertThat("06.12.34.56.78".normalizePhone()).isEqualTo("0612345678")
        assertThat("(06) 12-34 56 78".normalizePhone()).isEqualTo("0612345678")
    }

    @Test
    fun `normalizePhone strips plus when not leading`() {
        assertThat("06+12".normalizePhone()).isEqualTo("0612")
    }

    @Test
    fun `normalizePhone keeps star and hash for USSD codes`() {
        assertThat("*100#".normalizePhone()).isEqualTo("*100#")
    }

    @Test
    fun `normalizePhone returns empty for non-numeric input`() {
        assertThat("Free".normalizePhone()).isEqualTo("")
    }

    // === PhoneAddress ===

    @Test
    fun `PhoneAddress falls back to trimmed raw when normalize would be empty`() {
        val p = PhoneAddress.of("  Orange  ")
        assertThat(p.raw).isEqualTo("Orange")
        assertThat(p.normalized).isEqualTo("Orange")
    }

    @Test
    fun `PhoneAddress isValidDispatchTarget true when normalized has digits`() {
        assertThat(PhoneAddress.of("+33612345678").isValidDispatchTarget).isTrue()
        assertThat(PhoneAddress.of("0612345678").isValidDispatchTarget).isTrue()
    }

    @Test
    fun `PhoneAddress isValidDispatchTarget false for alphanumeric senders`() {
        assertThat(PhoneAddress.of("Orange").isValidDispatchTarget).isFalse()
        assertThat(PhoneAddress.of("INFO").isValidDispatchTarget).isFalse()
    }

    // === EmergencyTemplate surface ===

    @Test
    fun `EmergencyTemplate has exactly 3 entries with distinct body and label resources`() {
        val entries = EmergencyTemplate.entries
        assertThat(entries).hasSize(3)
        assertThat(entries.map { it.bodyTemplateRes }.toSet()).hasSize(3)
        assertThat(entries.map { it.labelRes }.toSet()).hasSize(3)
    }

    @Test
    fun `EmergencyTemplate enum order preserves NEED_HELP first`() {
        // The default in AppSettings.emergency.template is NEED_HELP — locking the ordinal here
        // catches accidental reorders that would silently change the default in DataStore JSON
        // for users who never opened the picker.
        assertThat(EmergencyTemplate.entries[0]).isEqualTo(EmergencyTemplate.NEED_HELP)
    }

    // === Log redaction ===

    @Test
    fun `redactPhone keeps short prefix and last 2 digits`() {
        assertThat("+33612345678".redactPhone()).startsWith("+33")
        assertThat("+33612345678".redactPhone()).endsWith("78")
        assertThat("+33612345678".redactPhone()).doesNotContain("12345")
    }

    @Test
    fun `redactPhone handles national format`() {
        assertThat("0612345678".redactPhone()).startsWith("06")
        assertThat("0612345678".redactPhone()).endsWith("78")
        assertThat("0612345678".redactPhone()).doesNotContain("123456")
    }

    @Test
    fun `redactPhone collapses short input safely`() {
        assertThat("12".redactPhone()).isEqualTo("***")
    }

    // === Default cascade priority ===

    @Test
    fun `default cascade priority for new contact is 0 meaning SMS only`() {
        // EmergencyContactEntity default — locks the v0.2 semantics: priority 0 = receives SMS
        // but not in the cascade call order. TriggerEmergencyUseCase sorts ascending so 0 comes
        // before 1, meaning "not in cascade" contacts still receive SMS first.
        val defaultPriority = com.filestech.sos.data.local.db.entity.EmergencyContactEntity(
            displayName = "X",
            phoneNumber = "+33612345678",
        ).cascadePriority
        assertThat(defaultPriority).isEqualTo(0)
    }
}
