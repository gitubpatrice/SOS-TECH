package com.filestech.sos.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.filestech.sos.data.local.db.dao.EmergencyContactDao
import com.filestech.sos.data.local.db.entity.EmergencyContactEntity

/**
 * SOS Tech Room database — version 1.
 * Schema exported to app/schemas/ for migration testing.
 * SQLCipher passphrase injected via [DatabaseModule].
 *
 * All migrations MUST be strictly additive (ALTER ADD / CREATE INDEX / CREATE TABLE).
 * NEVER use fallbackToDestructiveMigration().
 */
@Database(
    entities = [EmergencyContactEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun emergencyContactDao(): EmergencyContactDao
}
