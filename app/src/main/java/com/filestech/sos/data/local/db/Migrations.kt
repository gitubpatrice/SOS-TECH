package com.filestech.sos.data.local.db

import androidx.room.migration.Migration

/**
 * Forward migrations for [AppDatabase]. All migrations MUST be strictly additive
 * (ALTER ADD COLUMN, CREATE INDEX, CREATE TABLE) so that existing rows are never rewritten
 * and the SQLCipher passphrase remains valid across the bump — `adb install -r` upgrades
 * transparently without re-prompting the user for setup.
 *
 * v0.1 ships at schema version 1 → no migrations yet. The first ALTER will land here.
 */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
