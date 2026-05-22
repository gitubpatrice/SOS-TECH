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
 * Implementation: [WebhookDispatcher] (v0.2+ — OkHttp not yet added).
 */
@Serializable
data class WebhookConfig(
    val enabled: Boolean = false,
    val url: String = "",
    val includeGps: Boolean = false,
)

/** Minimal payload — no PII beyond optional GPS. */
@Serializable
data class WebhookPayload(
    val trigger: String = "emergency",
    val ts: Long,
    val lat: Double? = null,
    val lng: Double? = null,
)

interface WebhookDispatcher {
    /**
     * Dispatch [payload] to the configured [WebhookConfig.url].
     * Retries up to [WEBHOOK_MAX_RETRIES] times with exponential backoff.
     * Requires INTERNET permission.
     */
    suspend fun dispatch(payload: WebhookPayload): com.filestech.sos.core.result.Outcome<Unit>
}
