package com.filestech.sos.domain.cascade

import kotlinx.serialization.Serializable

/**
 * Configuration for the automatic call cascade feature.
 *
 * When enabled, tapping "Appeler proches" calls contact #1; if no answer within
 * [noAnswerTimeoutMs], hangs up and calls contact #2, and so on.
 *
 * Requires READ_PHONE_STATE to detect call state transitions.
 * Implementation: [CascadeDialerService] (v0.2+).
 */
@Serializable
data class CascadeConfig(
    val enabled: Boolean = false,
    /** Ordered list of contact IDs (from EmergencyContact) to call in sequence. */
    val contactPriorityOrder: List<Long> = emptyList(),
    /** Milliseconds to wait for answer before moving to next contact. */
    val noAnswerTimeoutMs: Long = 10_000L,
)

/**
 * Contract for the cascade dialer. Implementation is a TODO for v0.2.
 * Requires [CascadeDialerService] ForegroundService + READ_PHONE_STATE permission.
 */
interface CascadeDialer {
    /** Start the cascade from the beginning of [CascadeConfig.contactPriorityOrder]. */
    suspend fun startCascade()

    /** Abort the cascade and hang up any current call. */
    suspend fun abortCascade()

    /** Returns true if a cascade is currently in progress. */
    fun isRunning(): Boolean
}
