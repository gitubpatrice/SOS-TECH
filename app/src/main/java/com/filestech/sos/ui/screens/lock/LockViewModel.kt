package com.filestech.sos.ui.screens.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.sos.data.local.datastore.LockMode
import com.filestech.sos.data.local.datastore.SettingsRepository
import com.filestech.sos.di.IoDispatcher
import com.filestech.sos.security.AppLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LockEvent {
    data object InvalidPin : LockEvent
    data object BiometricFailed : LockEvent
}

@HiltViewModel
class LockViewModel @Inject constructor(
    // KQ-2: private — callers use dedicated public methods (beginBiometricChallenge,
    // markBiometricUnlocked) rather than reaching into the manager directly.
    private val appLockManager: AppLockManager,
    private val settings: SettingsRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    val state: StateFlow<AppLockManager.LockState> = appLockManager.state
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = appLockManager.state.value,
        )

    /** True when lockMode == BIOMETRIC and a PIN is configured (biometric is an add-on). */
    val isBiometricEnabled: StateFlow<Boolean> = settings.flow
        .map { it.security.lockMode == LockMode.BIOMETRIC }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = false,
        )

    private val _events = Channel<LockEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun attemptUnlock(pin: CharArray) {
        viewModelScope.launch(io) {
            val result = appLockManager.attemptUnlock(pin)
            if (result is AppLockManager.LockState.Locked ||
                result is AppLockManager.LockState.LockedOut
            ) {
                _events.send(LockEvent.InvalidPin)
            }
        }
    }

    fun beginBiometricChallenge(): ByteArray = appLockManager.beginBiometricChallenge()

    fun markBiometricUnlocked(challenge: ByteArray) {
        appLockManager.markBiometricUnlocked(challenge)
    }

    fun onBiometricError() {
        viewModelScope.launch { _events.send(LockEvent.BiometricFailed) }
    }
}
