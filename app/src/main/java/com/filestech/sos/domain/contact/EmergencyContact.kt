package com.filestech.sos.domain.contact

import kotlinx.serialization.Serializable

/**
 * A contact designated by the user to be called / messaged in an emergency.
 *
 * Note: SOS Tech stores its own contact list independently of SMS Tech (v0.1).
 * Future: factor into `files-tech-emergency-core` AAR consumed by both apps.
 */
@Serializable
data class EmergencyContact(
    val id: Long = 0L,
    val displayName: String,
    val phoneNumber: String,
    /** Position in the cascade call order (1-based, 0 = not in cascade). */
    val cascadePriority: Int = 0,
    val addedAtMs: Long = System.currentTimeMillis(),
)
