package com.filestech.sos.domain.livegps

import kotlinx.serialization.Serializable

/**
 * Configuration for the live GPS sharing feature.
 *
 * When enabled, starts a WorkManager worker that sends GPS coordinates to [contactPriorityOrder]
 * every 30 s for up to [durationMin] minutes.
 * Requires ACCESS_FINE_LOCATION + ACCESS_BACKGROUND_LOCATION.
 * Kill-switch: UI button or automatic stop at [durationMin] expiry.
 *
 * Implementation: [LiveGpsWorker] (v0.2+).
 */
@Serializable
data class LiveGpsConfig(
    val enabled: Boolean = false,
    val contactPriorityOrder: List<Long> = emptyList(),
    /** Duration in minutes for live GPS sharing session. */
    val durationMin: Int = 5,
)
