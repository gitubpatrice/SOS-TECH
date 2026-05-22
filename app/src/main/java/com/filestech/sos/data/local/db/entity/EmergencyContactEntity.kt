package com.filestech.sos.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "emergency_contacts")
data class EmergencyContactEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0L,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "phone_number") val phoneNumber: String,
    @ColumnInfo(name = "cascade_priority") val cascadePriority: Int = 0,
    @ColumnInfo(name = "added_at_ms") val addedAtMs: Long = System.currentTimeMillis(),
)
