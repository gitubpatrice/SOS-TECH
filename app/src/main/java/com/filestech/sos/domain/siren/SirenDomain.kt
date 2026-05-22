package com.filestech.sos.domain.siren

/** Maximum siren duration before automatic stop. Safety guard against battery drain. */
const val SIREN_MAX_DURATION_MS = 5 * 60 * 1_000L // 5 minutes

/**
 * Contract for the siren controller.
 *
 * Implementation uses MediaPlayer for audio + CameraManager for rear flashlight strobe.
 * Sound file is NOT bundled — downloaded via SAF (v0.2+).
 * Flash requires CAMERA permission (flash-only, no preview surface needed).
 */
interface SirenController {
    /**
     * Start audio siren + flash strobe.
     * Auto-stops after [SIREN_MAX_DURATION_MS] milliseconds.
     */
    suspend fun start()

    /** Stop the siren immediately. Idempotent. */
    suspend fun stop()

    /** Returns true if the siren is currently active. */
    fun isActive(): Boolean
}
