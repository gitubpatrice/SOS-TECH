package com.filestech.sos.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.sos.data.local.datastore.SettingsRepository
import com.filestech.sos.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val shortcutNotifEnabled: Boolean = false,
    val voiceEnabled: Boolean = false,
    val cascadeEnabled: Boolean = false,
    val sirenEnabled: Boolean = false,
    val liveGpsEnabled: Boolean = false,
    val recordingEnabled: Boolean = false,
    val webhookEnabled: Boolean = false,
    val flagSecure: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = settings.flow
        .map { s ->
            SettingsUiState(
                shortcutNotifEnabled = s.emergency.shortcutNotifEnabled,
                voiceEnabled = s.voice.enabled,
                cascadeEnabled = s.cascade.enabled,
                sirenEnabled = s.emergency.sirenEnabled,
                liveGpsEnabled = s.liveGps.enabled,
                recordingEnabled = s.recording.enabled,
                webhookEnabled = s.webhook.enabled,
                flagSecure = s.security.flagSecure,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = SettingsUiState(),
        )

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

    private fun update(transform: (com.filestech.sos.data.local.datastore.AppSettings) -> com.filestech.sos.data.local.datastore.AppSettings) {
        viewModelScope.launch(io) { settings.update(transform) }
    }
}
