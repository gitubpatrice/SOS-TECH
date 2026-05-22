package com.filestech.sos.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Re-locks the app whenever it transitions to background (ProcessLifecycleOwner.onStop).
 *
 * Port from SMS Tech. Wire in MainApplication.onCreate via [register].
 * forceLock() is idempotent on LockState.Disabled so no special guard needed.
 */
@Singleton
class AutoLockObserver @Inject constructor(
    private val appLockManager: AppLockManager,
) : DefaultLifecycleObserver {

    fun register() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        Timber.d("AutoLockObserver: registered on ProcessLifecycleOwner")
    }

    override fun onStop(owner: LifecycleOwner) {
        Timber.d("AutoLockObserver: app went to background — forcing lock")
        appLockManager.forceLock()
    }
}
