package com.filestech.sos.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Single-fix location resolver for the emergency SMS path.
 *
 * Deliberately minimalist:
 *  - One fix, no continuous tracking.
 *  - No `FusedLocationProviderClient` (Google Play Services dependency would break F-Droid).
 *  - No accuracy filtering — an imprecise fix beats no fix in an emergency context.
 *
 * Cascade strategy:
 *  1. Permission check (`ACCESS_FINE_LOCATION`). If denied → `null`.
 *  2. Best fresh `lastKnownLocation` (≤ [FRESH_THRESHOLD_MS] old) → return immediately.
 *  3. Listen on both GPS + NETWORK providers in parallel, accept the **first** fix
 *     to arrive within [timeoutMs] (default 8 s).
 *  4. On timeout: fall back to whatever `lastKnownLocation` is available (even stale) or `null`.
 *
 * Listeners are removed in both `onLocationChanged` and `invokeOnCancellation`, with an
 * `AtomicBoolean` guarding against double-resume when GPS and NETWORK fire near-simultaneously.
 */
@Singleton
class LocationResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Returns the best available location within [timeoutMs], or `null`. Never throws. */
    suspend fun getCurrentLocation(timeoutMs: Long = DEFAULT_TIMEOUT_MS): Location? {
        if (!hasLocationPermission()) {
            Timber.d("LocationResolver: ACCESS_FINE_LOCATION not granted")
            return null
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) {
            Timber.w("LocationResolver: LocationManager unavailable")
            return null
        }

        bestFreshLastKnown(lm)?.let { fresh ->
            Timber.d("LocationResolver: fresh lastKnown provider=%s ageMs=%d", fresh.provider, System.currentTimeMillis() - fresh.time)
            return fresh
        }

        val gpsEnabled = runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
        val networkEnabled = runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)
        if (!gpsEnabled && !networkEnabled) {
            Timber.d("LocationResolver: no provider enabled, returning stale lastKnown if any")
            return bestLastKnown(lm)
        }

        val freshFix = withTimeoutOrNull(timeoutMs) {
            awaitFirstFix(lm, gpsEnabled, networkEnabled)
        }
        if (freshFix != null) {
            Timber.d("LocationResolver: fresh fix provider=%s", freshFix.provider)
            return freshFix
        }
        val stale = bestLastKnown(lm)
        if (stale != null) {
            Timber.d("LocationResolver: timeout, stale lastKnown provider=%s ageMs=%d", stale.provider, System.currentTimeMillis() - stale.time)
        } else {
            Timber.d("LocationResolver: timeout, no lastKnown available")
        }
        return stale
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun bestFreshLastKnown(lm: LocationManager): Location? {
        val now = System.currentTimeMillis()
        return bestLastKnown(lm)?.takeIf { now - it.time <= FRESH_THRESHOLD_MS }
    }

    private fun bestLastKnown(lm: LocationManager): Location? {
        val candidates = listOfNotNull(
            safeLastKnown(lm, LocationManager.GPS_PROVIDER),
            safeLastKnown(lm, LocationManager.NETWORK_PROVIDER),
        )
        return candidates.maxByOrNull { it.time }
    }

    private fun safeLastKnown(lm: LocationManager, provider: String): Location? =
        try {
            @Suppress("MissingPermission") // Gated by hasLocationPermission() upstream.
            lm.getLastKnownLocation(provider)
        } catch (t: SecurityException) {
            // Permission revoked between check and call — defense in depth.
            Timber.w(t, "LocationResolver: lastKnown SecurityException on %s", provider)
            null
        } catch (t: IllegalArgumentException) {
            Timber.w(t, "LocationResolver: lastKnown unknown provider %s", provider)
            null
        }

    @Suppress("MissingPermission") // Upstream guard via hasLocationPermission().
    private suspend fun awaitFirstFix(
        lm: LocationManager,
        gpsEnabled: Boolean,
        networkEnabled: Boolean,
    ): Location? = suspendCancellableCoroutine { cont ->
        val handler = Handler(Looper.getMainLooper())
        val listenerHolder = arrayOfNulls<LocationListener>(1)
        // GPS and NETWORK can resolve near-simultaneously: without this flag the second
        // callback resumes a coroutine already terminated → IllegalStateException.
        val resumed = AtomicBoolean(false)

        fun cleanup() {
            listenerHolder[0]?.let { l ->
                runCatching { lm.removeUpdates(l) }
                listenerHolder[0] = null
            }
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (!resumed.compareAndSet(false, true)) return
                cleanup()
                if (cont.isActive) cont.resume(location)
            }

            @Deprecated("Required for API < 30 compat — keep override or AbstractMethodError at runtime")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        listenerHolder[0] = listener

        cont.invokeOnCancellation { cleanup() }

        try {
            if (gpsEnabled) lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener, handler.looper)
            if (networkEnabled) lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener, handler.looper)
        } catch (t: SecurityException) {
            Timber.w(t, "LocationResolver: requestLocationUpdates SecurityException")
            // Cleanup unconditionally: a GPS listener may already be registered when NETWORK
            // throws — without this cleanup it would leak (battery drain).
            cleanup()
            if (resumed.compareAndSet(false, true) && cont.isActive) cont.resume(null)
        }
    }

    companion object {
        /** Single-fix timeout (8 s). Beyond that we serve stale data rather than block the SMS. */
        const val DEFAULT_TIMEOUT_MS: Long = 8_000L

        /** Max age of a `lastKnown` we consider fresh enough to skip the new-fix attempt. */
        const val FRESH_THRESHOLD_MS: Long = 5 * 60 * 1000L
    }
}
