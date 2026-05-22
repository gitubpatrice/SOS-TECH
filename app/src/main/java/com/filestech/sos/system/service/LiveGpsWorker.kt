package com.filestech.sos.system.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import timber.log.Timber

/**
 * WorkManager worker for live GPS sharing.
 *
 * Runs as a long-running worker sending location updates to configured contacts
 * every 30 s for up to [LiveGpsConfig.durationMin] minutes.
 *
 * Requires ACCESS_FINE_LOCATION + ACCESS_BACKGROUND_LOCATION.
 * Kill-switch: UI button or automatic expiry at [durationMin] minutes.
 *
 * Implementation: v0.2+
 */
class LiveGpsWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // TODO v0.2: implement location updates + SMS dispatch to contacts
        Timber.d("LiveGpsWorker: stub v0.1 — no-op")
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "sos_live_gps"
    }
}
