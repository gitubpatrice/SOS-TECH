package com.filestech.sos.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.filestech.sos.data.local.db.dao.EmergencyContactDao
import com.filestech.sos.data.local.db.entity.EmergencyContactEntity

/**
 * SOS Tech Room database — schema version 1.
 *
 * - Schema is exported to `app/schemas/` and committed (cf. `.gitignore`). Schema files drive
 *   Room migration tests in `app/src/test/`.
 * - SQLCipher passphrase derivation is handled in [DatabaseFactory] / [DatabaseKeyManager];
 *   this class is intentionally crypto-agnostic.
 * - All forward migrations MUST be strictly additive (ALTER ADD / CREATE INDEX / CREATE TABLE)
 *   to preserve the passphrase across version bumps. See [Migrations].
 */
@Database(
    entities = [EmergencyContactEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun emergencyContactDao(): EmergencyContactDao

    companion object {
        const val DATABASE_NAME = "sos_tech.db"
    }
}
