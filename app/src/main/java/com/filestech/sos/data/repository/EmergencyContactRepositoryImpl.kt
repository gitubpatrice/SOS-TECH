package com.filestech.sos.data.repository

import com.filestech.sos.core.ext.normalizePhone
import com.filestech.sos.core.ext.stripUnsafe
import com.filestech.sos.core.result.AppError
import com.filestech.sos.core.result.Outcome
import com.filestech.sos.core.result.runCatchingOutcome
import com.filestech.sos.data.local.db.dao.EmergencyContactDao
import com.filestech.sos.data.local.db.entity.EmergencyContactEntity
import com.filestech.sos.di.IoDispatcher
import com.filestech.sos.domain.contact.EmergencyContact
import com.filestech.sos.domain.contact.EmergencyContactRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [EmergencyContactRepository].
 *
 * - All mutations validate `displayName` and `phoneNumber` BEFORE touching the DB.
 *   Invalid input → `Outcome.Failure(AppError.Validation)` with a precise reason.
 * - `displayName` is stripped of bidi/zero-width controls (`stripUnsafe`) before storage —
 *   defends against forged contact names that mimic emergency services in the UI.
 * - `phoneNumber` is normalized (`normalizePhone`) so two equivalent forms collide on save
 *   and so dispatch downstream needs no further cleanup.
 * - All work runs on the injected IO dispatcher.
 */
@Singleton
class EmergencyContactRepositoryImpl @Inject constructor(
    private val dao: EmergencyContactDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) : EmergencyContactRepository {

    override fun observeAll(): Flow<List<EmergencyContact>> =
        dao.observeAll().map { rows -> rows.map(::toDomain) }.flowOn(io)

    override suspend fun getAll(): List<EmergencyContact> = withContext(io) {
        dao.getAll().map(::toDomain)
    }

    override suspend fun add(displayName: String, phoneNumber: String, cascadePriority: Int): Outcome<Long> =
        withContext(io) {
            val cleanName = displayName.stripUnsafe()
            if (cleanName.isBlank()) return@withContext Outcome.Failure(AppError.Validation("display_name_blank"))
            val cleanPhone = phoneNumber.normalizePhone()
            if (cleanPhone.isBlank()) return@withContext Outcome.Failure(AppError.Validation("phone_blank"))
            if (!cleanPhone.any { it.isDigit() }) return@withContext Outcome.Failure(AppError.Validation("phone_no_digits"))
            if (cascadePriority < 0) return@withContext Outcome.Failure(AppError.Validation("cascade_priority_negative"))

            runCatchingOutcome(
                block = {
                    dao.insert(
                        EmergencyContactEntity(
                            displayName = cleanName,
                            phoneNumber = cleanPhone,
                            cascadePriority = cascadePriority,
                        ),
                    )
                },
                errorMapper = { AppError.Database(it) },
            )
        }

    override suspend fun update(contact: EmergencyContact): Outcome<Unit> = withContext(io) {
        val cleanName = contact.displayName.stripUnsafe()
        if (cleanName.isBlank()) return@withContext Outcome.Failure(AppError.Validation("display_name_blank"))
        val cleanPhone = contact.phoneNumber.normalizePhone()
        if (cleanPhone.isBlank() || !cleanPhone.any { it.isDigit() }) {
            return@withContext Outcome.Failure(AppError.Validation("phone_invalid"))
        }
        if (contact.cascadePriority < 0) return@withContext Outcome.Failure(AppError.Validation("cascade_priority_negative"))
        if (contact.id <= 0L) return@withContext Outcome.Failure(AppError.Validation("missing_id"))

        runCatchingOutcome(
            block = {
                dao.update(
                    EmergencyContactEntity(
                        id = contact.id,
                        displayName = cleanName,
                        phoneNumber = cleanPhone,
                        cascadePriority = contact.cascadePriority,
                        addedAtMs = contact.addedAtMs,
                    ),
                )
            },
            errorMapper = { AppError.Database(it) },
        )
    }

    override suspend fun deleteById(id: Long): Outcome<Unit> = withContext(io) {
        if (id <= 0L) return@withContext Outcome.Failure(AppError.Validation("missing_id"))
        runCatchingOutcome(
            block = { dao.deleteById(id) },
            errorMapper = { AppError.Database(it) },
        )
    }

    private fun toDomain(e: EmergencyContactEntity): EmergencyContact = EmergencyContact(
        id = e.id,
        displayName = e.displayName,
        phoneNumber = e.phoneNumber,
        cascadePriority = e.cascadePriority,
        addedAtMs = e.addedAtMs,
    )
}
