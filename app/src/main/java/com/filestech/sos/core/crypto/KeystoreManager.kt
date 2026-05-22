package com.filestech.sos.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around the AndroidKeyStore for AES-256-GCM keys.
 *
 * One key per logical purpose (SOS Tech aliases):
 *  - "sostech_db_master"     : SQLCipher master key (call logs + recording vault)
 *  - "sostech_recording_kek" : KEK for audio recording vault entries (recording-only payload)
 *  - "sostech_settings_aead" : encrypts sensitive DataStore entries
 *  - "sostech_panic_decoy"   : panic-mode decoy key (separate from db_master)
 *
 * Keys are non-exportable; AES-GCM nonces are caller-provided 12-byte arrays (cf. [AeadCipher]).
 */
@Singleton
class KeystoreManager @Inject constructor() {

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    fun getOrCreateKey(alias: String, userAuthRequired: Boolean = false): SecretKey {
        keyStore.getKey(alias, null)?.let { return it as SecretKey }
        return generateKey(alias, userAuthRequired)
    }

    fun deleteKey(alias: String) {
        runCatching { keyStore.deleteEntry(alias) }
            .onFailure { Timber.w(it, "KeystoreManager: failed to delete %s", alias) }
    }

    fun containsAlias(alias: String): Boolean = keyStore.containsAlias(alias)

    private fun generateKey(alias: String, userAuthRequired: Boolean): SecretKey {
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            // AndroidKeyStore rejects user-supplied IVs when randomization is required.
            // [AeadCipher.encrypt] always supplies a fresh 96-bit IV via SecureRandom, so
            // randomization is cryptographically enforced at the call site.
            .setRandomizedEncryptionRequired(false)
            .setUserAuthenticationRequired(userAuthRequired)
            .apply {
                if (userAuthRequired && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    setInvalidatedByBiometricEnrollment(true)
                }
            }
            .build()
        keyGen.init(spec)
        return keyGen.generateKey()
    }

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_SIZE_BITS = 256

        const val ALIAS_DB_MASTER = "sostech_db_master"
        const val ALIAS_RECORDING_KEK = "sostech_recording_kek"
        const val ALIAS_SETTINGS_AEAD = "sostech_settings_aead"
        const val ALIAS_PANIC_DECOY = "sostech_panic_decoy"
    }
}
