package com.filestech.sos

import com.filestech.sos.core.crypto.AeadCipher
import com.filestech.sos.core.crypto.KeystoreManager
import com.filestech.sos.core.crypto.wipe
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
import java.security.SecureRandom

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

    // === CRYPTO INVARIANTS — must never weaken across versions ===

    @Test
    fun `Keystore aliases are SOS-namespaced and pairwise distinct`() {
        val aliases = setOf(
            KeystoreManager.ALIAS_DB_MASTER,
            KeystoreManager.ALIAS_RECORDING_KEK,
            KeystoreManager.ALIAS_SETTINGS_AEAD,
            KeystoreManager.ALIAS_PANIC_DECOY,
        )
        assertThat(aliases).hasSize(4)
        aliases.forEach { assertThat(it).startsWith("sostech_") }
    }

    @Test
    fun `Keystore key size is AES-256`() {
        assertThat(KeystoreManager.KEY_SIZE_BITS).isEqualTo(256)
    }

    @Test
    fun `AEAD format constants — AES-256-GCM with 12-byte IV and 128-bit tag`() {
        assertThat(AeadCipher.KEY_BYTES).isEqualTo(32)
        assertThat(AeadCipher.IV_SIZE).isEqualTo(12)
        assertThat(AeadCipher.TAG_BITS).isEqualTo(128)
        assertThat(AeadCipher.VERSION).isEqualTo(0x01.toByte())
        assertThat(AeadCipher.TRANSFORMATION).isEqualTo("AES/GCM/NoPadding")
    }

    @Test
    fun `AEAD raw round-trip preserves plaintext and produces versioned envelope`() {
        val rawKey = ByteArray(AeadCipher.KEY_BYTES).also(SecureRandom()::nextBytes)
        val cipher = AeadCipher()
        val plaintext = "SOS Tech v0.1 crypto smoke test".toByteArray(Charsets.UTF_8)

        val blob = (cipher.encryptRaw(rawKey, plaintext) as Outcome.Success).value
        assertThat(blob[0]).isEqualTo(AeadCipher.VERSION)
        assertThat(blob.size).isEqualTo(1 + AeadCipher.IV_SIZE + plaintext.size + 16) // +16-byte tag

        val recovered = (cipher.decryptRaw(rawKey, blob) as Outcome.Success).value
        assertThat(recovered).isEqualTo(plaintext)
        rawKey.wipe()
    }

    @Test
    fun `AEAD rejects mangled ciphertext via GCM auth tag`() {
        val rawKey = ByteArray(AeadCipher.KEY_BYTES).also(SecureRandom()::nextBytes)
        val cipher = AeadCipher()
        val plaintext = "tamper-test".toByteArray(Charsets.UTF_8)
        val blob = (cipher.encryptRaw(rawKey, plaintext) as Outcome.Success).value

        // Flip a bit in the ciphertext (after version + IV).
        val tampered = blob.copyOf().also { it[1 + AeadCipher.IV_SIZE] = (it[1 + AeadCipher.IV_SIZE].toInt() xor 0x01).toByte() }

        val result = cipher.decryptRaw(rawKey, tampered)
        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat((result as Outcome.Failure).error).isInstanceOf(AppError.Crypto::class.java)
        rawKey.wipe()
    }

    @Test
    fun `AEAD rejects unsupported envelope version`() {
        val rawKey = ByteArray(AeadCipher.KEY_BYTES).also(SecureRandom()::nextBytes)
        val cipher = AeadCipher()
        val blob = (cipher.encryptRaw(rawKey, "x".toByteArray()) as Outcome.Success).value

        val wrongVersion = blob.copyOf().also { it[0] = 0xFF.toByte() }
        val result = cipher.decryptRaw(rawKey, wrongVersion)
        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        rawKey.wipe()
    }

    @Test
    fun `ByteArray wipe zeroes the buffer in place`() {
        val secret = ByteArray(32) { 0xAB.toByte() }
        secret.wipe()
        assertThat(secret.all { it == 0.toByte() }).isTrue()
    }

    @Test
    fun `Database name is namespaced to SOS Tech`() {
        assertThat(com.filestech.sos.data.local.db.AppDatabase.DATABASE_NAME).isEqualTo("sos_tech.db")
    }
}
