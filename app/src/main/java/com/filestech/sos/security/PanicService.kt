package com.filestech.sos.security

import android.content.Context
import com.filestech.sos.core.crypto.KeystoreManager
import com.filestech.sos.data.local.datastore.AppSettings
import com.filestech.sos.data.local.datastore.SecurityStore
import com.filestech.sos.data.local.datastore.SettingsRepository
import com.filestech.sos.data.local.db.AppDatabase
import com.filestech.sos.data.local.db.DatabaseKeyManager
import com.filestech.sos.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hard wipe of all locally stored sensitive data. Triggered by user action (settings →
 * "Effacer toutes les données"). Order matters: drop the SQLCipher key file first so even
 * a crash mid-wipe leaves the DB unreadable.
 *
 * Wipe order doctrine (audit F29 from SMS Tech):
 *  1. Close the Room/SQLCipher database — no transaction can re-write after delete.
 *  2. Drop the wrapped DB key BEFORE touching the actual database files — crash mid-wipe
 *     leaves the residual DB unreadable.
 *  3. Drop Keystore aliases — wrapped key blob cannot be reconstructed.
 *  4. deleteDatabase — nukes <db>-journal, <db>-wal, <db>-shm.
 *  5. Wipe: cache + exports + db dir (no mms_attachments dir in SOS Tech).
 *  6. Reset security store: clearPin + clearPanic + reset failCount/lockoutUntil.
 *  7. Reset settings to defaults.
 *
 * SOS Tech adaptation from SMS Tech:
 *  - ALIAS_VAULT_KEK → ALIAS_RECORDING_KEK (SOS Tech naming).
 *  - DATABASE_NAME from AppDatabase.DATABASE_NAME.
 *  - No "mms_attachments" dir (not applicable to SOS Tech).
 */
@Singleton
class PanicService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val keyManager: DatabaseKeyManager,
    private val keystore: KeystoreManager,
    private val securityStore: SecurityStore,
    private val settings: SettingsRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun nukeEverything(): Unit = withContext(io) {
        runCatching { database.close() }.onFailure { Timber.w(it, "PanicService: db close") }
        runCatching { keyManager.destroyKeyFile() }.onFailure { Timber.w(it, "PanicService: destroy key file") }
        runCatching {
            keystore.deleteKey(KeystoreManager.ALIAS_DB_MASTER)
            keystore.deleteKey(KeystoreManager.ALIAS_RECORDING_KEK)
            keystore.deleteKey(KeystoreManager.ALIAS_SETTINGS_AEAD)
            keystore.deleteKey(KeystoreManager.ALIAS_PANIC_DECOY)
        }.onFailure { Timber.w(it, "PanicService: delete keystore aliases") }
        runCatching { context.deleteDatabase(AppDatabase.DATABASE_NAME) }
            .onFailure { Timber.w(it, "PanicService: deleteDatabase") }
        runCatching { securityStore.clearPin() }
        runCatching { securityStore.clearPanic() }
        // Audit S-P2-2: clear bookkeeping too; otherwise the new session inherits old fail
        // counters and could lock the user out before they can configure a new PIN.
        runCatching {
            securityStore.setFailCount(0)
            securityStore.setLockoutUntil(0L)
        }
        runCatching {
            File(context.filesDir, "exports").deleteRecursively()
            File(context.filesDir, "db").deleteRecursively()
            context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        }.onFailure { Timber.w(it, "PanicService: wipe file dirs") }
        runCatching { settings.update { AppSettings() } }
    }
}
