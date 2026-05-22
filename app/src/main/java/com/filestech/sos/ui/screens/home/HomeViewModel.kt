package com.filestech.sos.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.sos.data.local.datastore.SettingsRepository
import com.filestech.sos.domain.contact.EmergencyContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val emergencyEnabled: Boolean = false,
    val voiceEnabled: Boolean = false,
    val cascadeEnabled: Boolean = false,
    val sirenEnabled: Boolean = false,
    val liveGpsEnabled: Boolean = false,
    val recordingEnabled: Boolean = false,
    val webhookEnabled: Boolean = false,
    /** True if at least one emergency contact is configured. */
    val contactsConfigured: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val contactRepository: EmergencyContactRepository,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        settings.flow,
        contactRepository.observeAll(),
    ) { s, contacts ->
        HomeUiState(
            emergencyEnabled = s.emergency.enabled,
            voiceEnabled = s.voice.enabled,
            cascadeEnabled = s.cascade.enabled,
            sirenEnabled = s.emergency.sirenEnabled,
            liveGpsEnabled = s.liveGps.enabled,
            recordingEnabled = s.recording.enabled && s.recording.userAcknowledgedLegalDisclaimer,
            webhookEnabled = s.webhook.enabled && s.webhook.url.isNotBlank(),
            contactsConfigured = contacts.isNotEmpty(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = HomeUiState(),
    )
}
