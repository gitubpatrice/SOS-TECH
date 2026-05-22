package com.filestech.sos.data.webhook

import com.filestech.sos.core.result.AppError
import com.filestech.sos.core.result.Outcome
import com.filestech.sos.di.IoDispatcher
import com.filestech.sos.domain.webhook.WEBHOOK_MAX_RETRIES
import com.filestech.sos.domain.webhook.WebhookDispatcher
import com.filestech.sos.domain.webhook.WebhookPayload
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real OkHttp-backed implementation of [WebhookDispatcher].
 *
 * Retry policy: up to [WEBHOOK_MAX_RETRIES] attempts with exponential backoff starting at 1 s.
 * 4xx responses are not retried (client error — retrying is pointless).
 * The call is always fire-and-forget from [TriggerEmergencyUseCase] — the emergency flow does
 * not wait for the HTTP response before completing.
 *
 * Privacy invariants:
 *  - JSON payload = {"trigger","ts","lat"(opt),"lng"(opt)} only.
 *  - No contact names, no phone numbers, no SMS body is ever included.
 *  - GPS coordinates are included only if [WebhookPayload.includeGps] = true AND fix available.
 *
 * URL logging: the configured URL is user-owned. We do not log it to prevent accidental
 * exfiltration of a shared secret embedded in the URL (e.g. HMAC token in query param).
 */
@Singleton
class WebhookDispatcherImpl @Inject constructor(
    @IoDispatcher private val io: CoroutineDispatcher,
) : WebhookDispatcher {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false) // retry loop managed here with explicit backoff
            .build()
    }

    override suspend fun dispatch(payload: WebhookPayload): Outcome<Unit> = withContext(io) {
        val url = payload.url.trim().takeIf { it.startsWith("https://") || it.startsWith("http://") }
            ?: return@withContext Outcome.Failure(AppError.Validation("invalid_webhook_url"))

        val json = buildJsonPayload(payload)
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())

        var lastError: Throwable? = null
        for (attempt in 0 until WEBHOOK_MAX_RETRIES) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .post(body)
                    .header("User-Agent", "SOS-Tech/0.4.0 (Files Tech)")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        Timber.i("WebhookDispatcher: dispatched attempt=%d code=%d", attempt + 1, resp.code)
                        return@withContext Outcome.Success(Unit)
                    }
                    // 4xx = client error — retrying will not help
                    if (resp.code in 400..499) {
                        Timber.w("WebhookDispatcher: 4xx client error code=%d — aborting retries", resp.code)
                        return@withContext Outcome.Failure(AppError.Network())
                    }
                    lastError = RuntimeException("HTTP ${resp.code}")
                    Timber.w("WebhookDispatcher: attempt %d server error code=%d", attempt + 1, resp.code)
                }
            } catch (t: Throwable) {
                lastError = t
                Timber.w(t, "WebhookDispatcher: attempt %d threw", attempt + 1)
            }
            // Exponential backoff: 1 s, 2 s, 4 s (capped at WEBHOOK_MAX_BACKOFF_MS)
            if (attempt < WEBHOOK_MAX_RETRIES - 1) {
                delay((1_000L * (1L shl attempt)).coerceAtMost(WEBHOOK_MAX_BACKOFF_MS))
            }
        }
        Timber.w("WebhookDispatcher: all %d attempts exhausted", WEBHOOK_MAX_RETRIES)
        Outcome.Failure(AppError.Network(lastError))
    }

    private fun buildJsonPayload(p: WebhookPayload): String {
        val sb = StringBuilder()
        sb.append("{\"trigger\":\"emergency\",\"ts\":").append(p.timestampMs)
        if (p.includeGps && p.latitude != null && p.longitude != null) {
            sb.append(",\"lat\":").append("%.5f".format(Locale.US, p.latitude))
            sb.append(",\"lng\":").append("%.5f".format(Locale.US, p.longitude))
        }
        sb.append("}")
        return sb.toString()
    }

    companion object {
        const val WEBHOOK_MAX_BACKOFF_MS = 8_000L
    }
}
