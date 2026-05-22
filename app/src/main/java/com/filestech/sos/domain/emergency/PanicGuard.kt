package com.filestech.sos.domain.emergency

import com.filestech.sos.security.AppLockManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gate that **all** features touching trusted contacts, recordings, or live data MUST consult
 * before acting. When the app is in "panic-decoy" mode (coerced unlock under duress), this
 * guard returns `true` and the consuming feature MUST silently refuse to act — otherwise an
 * attacker who forced the user to open the app would see the SMS depart, hear the siren start,
 * or see the contacts list rendered on screen, defeating the entire point of decoy mode.
 *
 * v0.3: real implementation backed by [AppLockManager.state]. Stub `DefaultPanicGuard` removed.
 */
interface PanicGuard {
    /** True ⇒ caller MUST suppress side-effects (no SMS, no Maps URL, no contact list render). */
    fun isPanicActive(): Boolean
}

@Singleton
class AppLockPanicGuard @Inject constructor(
    private val appLockManager: AppLockManager,
) : PanicGuard {
    override fun isPanicActive(): Boolean =
        appLockManager.state.value is AppLockManager.LockState.PanicDecoy
}
