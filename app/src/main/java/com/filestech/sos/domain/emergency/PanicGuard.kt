package com.filestech.sos.domain.emergency

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gate that **all** features touching trusted contacts, recording, or live data MUST consult
 * before acting. When the app is in "panic-decoy" mode (coerced unlock under duress), this
 * guard returns `true` and the consuming feature MUST silently refuse to act — otherwise an
 * attacker who forced the user to open the app would see the SMS depart, hear the siren start,
 * or see the contacts list rendered on screen, defeating the entire point of decoy mode.
 *
 * v0.2 status: stub always returning `false` (no panic mode wired yet). The contract is
 * defined now so feature code is correctly defensive from day one. v0.3 will inject the
 * real `PanicService` / `AppLockManager` (port from SMS Tech) under the same binding.
 */
interface PanicGuard {
    /** True ⇒ caller MUST suppress side-effects (no SMS, no Maps URL, no contact list render). */
    fun isPanicActive(): Boolean
}

@Singleton
class DefaultPanicGuard @Inject constructor() : PanicGuard {
    override fun isPanicActive(): Boolean = false // TODO v0.3: wire to PanicService.state
}
