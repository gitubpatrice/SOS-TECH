package com.filestech.sos.data.local.db

import android.content.Context
import androidx.room.Room
import com.filestech.sos.core.crypto.wipe
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the SQLCipher-backed [AppDatabase]. The raw passphrase is wiped from JVM memory
 * immediately after Room consumes the factory.
 *
 * The SQLCipher native library must be loaded once before the first connection is opened —
 * `System.loadLibrary("sqlcipher")` accomplishes this. SQLCipher's own `loadLibs()` helper has
 * existed in older releases but its signature has drifted across minor versions, so we call
 * the loader directly.
 *
 * **v0.1 policy**: any failure to derive the passphrase (Keystore invalidated, wrap corrupted,
 * I/O error) is propagated as [DatabaseKeyManager.Failure] up the Hilt graph, crashing the app
 * loudly at boot. This is the safe default: silent fallback would risk wiping user data without
 * consent. A future recovery UI in `MainActivity` will catch this typed failure and present the
 * user with informed choices (reset wallet, keep encrypted blob for analysis, etc.).
 *
 * **v0.1 migrations**: `fallbackToDestructiveMigrationOnDowngrade(dropAllTables = false)` is
 * deliberate. Downgrading from a future version that introduces ALTERs will refuse to open the
 * DB rather than silently dropping rows — the user must reinstall the matching version. Forward
 * migrations live in [Migrations] (empty for v1, will be populated when schema evolves).
 */
@Singleton
class DatabaseFactory @Inject constructor(
    private val keyManager: DatabaseKeyManager,
) {

    fun build(context: Context): AppDatabase {
        loadNativeOnce()
        val raw = keyManager.getOrCreatePassphrase()
        val factory = SupportOpenHelperFactory(raw)
        val db = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .openHelperFactory(factory)
            .addMigrations(*Migrations.ALL)
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = false)
            .build()
        raw.wipe()
        return db
    }

    @Synchronized
    private fun loadNativeOnce() {
        if (loaded) return
        System.loadLibrary("sqlcipher")
        loaded = true
    }

    companion object {
        @Volatile private var loaded = false
    }
}
