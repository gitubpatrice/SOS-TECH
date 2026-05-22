package com.filestech.sos.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.sos.data.local.datastore.AppSettings
import com.filestech.sos.data.local.datastore.LockMode
import com.filestech.sos.data.local.datastore.SecurityStore
import com.filestech.sos.data.local.datastore.SettingsRepository
import com.filestech.sos.di.IoDispatcher
import com.filestech.sos.domain.emergency.EmergencyTemplate
import com.filestech.sos.security.AppLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SettingsEvent {
    data object PinSetSuccess : SettingsEvent
    data object PinClearSuccess : SettingsEvent
    data object BiometricEnableSuccess : SettingsEvent
    data object BiometricEnableFailed : SettingsEvent
    data object PanicSetSuccess : SettingsEvent
    data object PanicClearSuccess : SettingsEvent
    data object NukeSuccess : SettingsEvent
    /** SEC-2: panic code candidate hashed to the same value as the primary PIN. */
    data object PanicSameAsPin : SettingsEvent
    data class Error(val message: String) : SettingsEvent
}

data class SettingsUiState(
    val shortcutNotifEnabled: Boolean = false,
    val voiceEnabled: Boolean = false,
    val cascadeEnabled: Boolean = false,
    val sirenEnabled: Boolean = false,
    val liveGpsEnabled: Boolean = false,
    val recordingEnabled: Boolean = false,
    val webhookEnabled: Boolean = false,
    val flagSecure: Boolean = true,
    val emergencyTemplate: EmergencyTemplate = EmergencyTemplate.NEED_HELP,
    val emergencyIncludeLocation: Boolean = false,
    val lockMode: LockMode = LockMode.OFF,
    val isPinConfigured: Boolean = false,
    val isPanicConfigured: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val appLockManager: AppLockManager,
    private val securityStore: SecurityStore,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        settings.flow,
        securityStore.hasPanic,
    ) { s, hasPanic ->
        SettingsUiState(
            shortcutNotifEnabled = s.emergency.shortcutNotifEnabled,
            voiceEnabled = s.voice.enabled,
            cascadeEnabled = s.cascade.enabled,
            sirenEnabled = s.emergency.sirenEnabled,
            liveGpsEnabled = s.liveGps.enabled,
            recordingEnabled = s.recording.enabled,
            webhookEnabled = s.webhook.enabled,
            flagSecure = s.security.flagSecure,
            emergencyTemplate = s.emergency.template,
            emergencyIncludeLocation = s.emergency.includeLocation,
            lockMode = s.security.lockMode,
            // isPinConfigured: lockMode != OFF means a PIN was set at some point.
            // We derive this from lockMode rather than hitting SecurityStore on each emission.
            isPinConfigured = s.security.lockMode != LockMode.OFF,
            // SEC-3: isPanicConfigured is now driven by a real Flow from SecurityStore
            // (was hardcoded false). User sees "Change panic code" accurately.
            isPanicConfigured = hasPanic,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = SettingsUiState(),
        )

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun toggleEmergencyShortcut(enabled: Boolean) = update { s ->
        s.copy(emergency = s.emergency.copy(shortcutNotifEnabled = enabled))
    }

    fun toggleVoice(enabled: Boolean) = update { s ->
        s.copy(voice = s.voice.copy(enabled = enabled))
    }

    fun toggleCascade(enabled: Boolean) = update { s ->
        s.copy(cascade = s.cascade.copy(enabled = enabled))
    }

    fun toggleSiren(enabled: Boolean) = update { s ->
        s.copy(emergency = s.emergency.copy(sirenEnabled = enabled))
    }

    fun toggleLiveGps(enabled: Boolean) = update { s ->
        s.copy(liveGps = s.liveGps.copy(enabled = enabled))
    }

    fun toggleRecording(enabled: Boolean) = update { s ->
        s.copy(recording = s.recording.copy(enabled = enabled))
    }

    fun toggleWebhook(enabled: Boolean) = update { s ->
        s.copy(webhook = s.webhook.copy(enabled = enabled))
    }

    fun toggleFlagSecure(enabled: Boolean) = update { s ->
        s.copy(security = s.security.copy(flagSecure = enabled))
    }

    fun selectTemplate(template: EmergencyTemplate) = update { s ->
        s.copy(emergency = s.emergency.copy(template = template))
    }

    fun toggleIncludeLocation(enabled: Boolean) = update { s ->
        s.copy(emergency = s.emergency.copy(includeLocation = enabled))
    }

    fun setPin(pin: CharArray) {
        viewModelScope.launch(io) {
            try {
                appLockManager.setPin(pin)
                _events.send(SettingsEvent.PinSetSuccess)
            } catch (e: Exception) {
                _events.send(SettingsEvent.Error(e.message ?: "PIN error"))
            }
        }
    }

    fun clearPin() {
        viewModelScope.launch(io) {
            appLockManager.clearPin()
            _events.send(SettingsEvent.PinClearSuccess)
        }
    }

    fun enableBiometric() {
        viewModelScope.launch(io) {
            val ok = appLockManager.enableBiometric()
            _events.send(if (ok) SettingsEvent.BiometricEnableSuccess else SettingsEvent.BiometricEnableFailed)
        }
    }

    fun disableBiometric() {
        viewModelScope.launch(io) {
            appLockManager.disableBiometric()
        }
    }

    fun setPanicCode(pin: CharArray) {
        viewModelScope.launch(io) {
            try {
                appLockManager.setPanicCode(pin)
                _events.send(SettingsEvent.PanicSetSuccess)
            } catch (e: IllegalArgumentException) {
                // SEC-2: panic code matches primary PIN — must stay distinct.
                if (e.message == "panic_same_as_pin") {
                    _events.send(SettingsEvent.PanicSameAsPin)
                } else {
                    _events.send(SettingsEvent.Error(e.message ?: "Panic code error"))
                }
            } catch (e: Exception) {
                _events.send(SettingsEvent.Error(e.message ?: "Panic code error"))
            }
        }
    }

    fun clearPanicCode() {
        viewModelScope.launch(io) {
            appLockManager.clearPanicCode()
            _events.send(SettingsEvent.PanicClearSuccess)
        }
    }

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch(io) { settings.update(transform) }
    }
}
