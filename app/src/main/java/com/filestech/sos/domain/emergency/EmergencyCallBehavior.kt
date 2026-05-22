package com.filestech.sos.domain.emergency

/**
 * Governs how the 112/15/17/18 call buttons behave.
 *
 * Three levels — the app is dedicated to emergency use so TAP_DIRECT_CALL is available here
 * (unlike SMS Tech which caps at HOLD_3S_DIRECT_CALL due to pocket-dial risk).
 * Default is HOLD_3S_DIRECT_CALL: explicit enough to prevent accidental triggers.
 */
enum class EmergencyCallBehavior {
    /** ACTION_DIAL — opens the system dialer pre-filled. No CALL_PHONE permission required. */
    DIALER_ONLY,

    /** Tap + hold 3 s → direct call via ACTION_CALL. Requires CALL_PHONE permission. */
    HOLD_3S_DIRECT_CALL,

    /**
     * Single tap → direct call. Requires CALL_PHONE permission.
     * User opt-in is explicit: shown only when CALL_PHONE is granted.
     * Warning displayed once on selection.
     */
    TAP_DIRECT_CALL,
}
