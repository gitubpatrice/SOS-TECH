package com.filestech.sos.ui.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.sos.data.local.datastore.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the first-launch welcome splash.
 *
 * - [shouldShow] reflects "the user has never seen this splash" — `true` until [markShown] is
 *   persisted to DataStore. The flag is eager-shared (`SharingStarted.Eagerly`) so the first
 *   cold-start composition sees the true persisted value without a single splash frame for users
 *   who already dismissed it.
 * - [markShown] is idempotent (DataStore JSON write of `splashShown = true`). The UI calls it
 *   exactly once from the dismiss guard.
 *
 * Only `Clear app data` in system Settings resets the flag — re-installing the APK on top of
 * existing data preserves it (firstInstallTime stable, DataStore preserved).
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    val shouldShow: StateFlow<Boolean> = settings.flow
        .map { !it.advanced.splashShown }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true,
        )

    fun markShown() {
        viewModelScope.launch {
            settings.update { it.copy(advanced = it.advanced.copy(splashShown = true)) }
        }
    }
}
