package com.filestech.sos.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.filestech.sos.data.local.db.entity.EmergencyContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmergencyContactDao {
    @Query("SELECT * FROM emergency_contacts ORDER BY cascade_priority ASC, added_at_ms ASC")
    fun observeAll(): Flow<List<EmergencyContactEntity>>

    @Query("SELECT * FROM emergency_contacts ORDER BY cascade_priority ASC, added_at_ms ASC")
    suspend fun getAll(): List<EmergencyContactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: EmergencyContactEntity): Long

    @Update
    suspend fun update(contact: EmergencyContactEntity)

    @Delete
    suspend fun delete(contact: EmergencyContactEntity)

    @Query("DELETE FROM emergency_contacts WHERE id = :id")
    suspend fun deleteById(id: Long)
}
