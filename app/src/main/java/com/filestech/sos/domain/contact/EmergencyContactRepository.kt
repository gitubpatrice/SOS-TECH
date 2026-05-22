package com.filestech.sos.domain.contact

import com.filestech.sos.core.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Persistence contract for the emergency contact list.
 *
 * Backed by Room (SQLCipher-encrypted, alias `sostech_db_master`). All mutations go through
 * this interface so the use-case layer is unaware of Room / DAOs.
 *
 * SOS Tech keeps its own contact list independent of SMS Tech (v0.2). A shared module
 * `files-tech-emergency-core` is under consideration but only after the surface stabilises
 * (we don't share a half-baked pattern across apps).
 */
interface EmergencyContactRepository {
    fun observeAll(): Flow<List<EmergencyContact>>

    suspend fun getAll(): List<EmergencyContact>

    suspend fun add(displayName: String, phoneNumber: String, cascadePriority: Int = 0): Outcome<Long>

    suspend fun update(contact: EmergencyContact): Outcome<Unit>

    suspend fun deleteById(id: Long): Outcome<Unit>
}
