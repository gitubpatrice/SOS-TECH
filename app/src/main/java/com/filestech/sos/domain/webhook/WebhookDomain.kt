package com.filestech.sos.domain.webhook

import kotlinx.serialization.Serializable

/** Maximum retry attempts for a webhook dispatch (exponential backoff). */
const val WEBHOOK_MAX_RETRIES = 3

/**
 * Configuration for the webhook dispatcher.
 *
 * Payload sent on emergency trigger:
 * `{"trigger":"emergency","ts":<epochMs>,"lat":<lat>,"lng":<lng>}`
 *
 * Privacy: no contact names, no SMS body, no recording data is ever sent.
 * GPS coordinates are included only if [includeGps] is true AND user has granted location permission.
 *
 * Implementation: [WebhookDispatcher] backed by OkHttp since v0.4.
 */
@Serializable
data class WebhookConfig(
    val enabled: Boolean = false,
    val url: String = "",
    val includeGps: Boolean = false,
)

/**
 * Dispatch request passed from [TriggerEmergencyUseCase] to [WebhookDispatcher].
 *
 * [url] — destination URL (must start with https:// or http://).
 * [timestampMs] — wall-clock epoch at trigger time.
 * [includeGps] — whether to include GPS coordinates in the JSON payload.
 * [latitude] / [longitude] — resolved GPS fix; present only when [includeGps] = true
 *   and a fix was available.
 */
data class WebhookPayload(
    val url: String,
    val timestampMs: Long,
    val includeGps: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

interface WebhookDispatcher {
    /**
     * Dispatch [payload] to [WebhookPayload.url].
     * Retries up to [WEBHOOK_MAX_RETRIES] times with exponential backoff.
     * Returns [com.filestech.sos.core.result.Outcome.Success] on first successful HTTP 2xx.
     * Returns [com.filestech.sos.core.result.Outcome.Failure] after all retries exhausted
     * or on 4xx (no retry for client errors).
     * Requires INTERNET permission.
     */
    suspend fun dispatch(payload: WebhookPayload): com.filestech.sos.core.result.Outcome<Unit>
}
