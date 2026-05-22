package com.filestech.sos.ui.screens.emergency

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.sos.data.local.datastore.SettingsRepository
import com.filestech.sos.di.IoDispatcher
import com.filestech.sos.domain.contact.EmergencyContact
import com.filestech.sos.domain.contact.EmergencyContactRepository
import com.filestech.sos.domain.emergency.EmergencyCallBehavior
import com.filestech.sos.domain.usecase.TriggerEmergencyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

data class EmergencyUiState(
    val emergencyEnabled: Boolean = false,
    val callBehavior: EmergencyCallBehavior = EmergencyCallBehavior.HOLD_3S_DIRECT_CALL,
    val trustedContacts: List<EmergencyContact> = emptyList(),
    val canTrigger: Boolean = true,
    val voiceEnabled: Boolean = false,
    val cascadeEnabled: Boolean = false,
    val sirenEnabled: Boolean = false,
    val liveGpsEnabled: Boolean = false,
    val recordingEnabled: Boolean = false,
    val webhookEnabled: Boolean = false,
) {
    val activeFeaturesCount: Int get() = listOf(
        voiceEnabled, cascadeEnabled, sirenEnabled, liveGpsEnabled, recordingEnabled, webhookEnabled,
    ).count { it }
}

/**
 * Side-channel events emitted to the UI for snackbars / dialogs / navigation. Modelled as a
 * channel-backed Flow so we never lose an event (Channel buffered) and never replay an old one
 * on configuration change (no StateFlow).
 */
sealed interface EmergencyEvent {
    data class TriggerSuccess(val sent: Int, val hadLocation: Boolean) : EmergencyEvent
    data class TriggerPartial(val sent: Int, val failed: Int) : EmergencyEvent
    data object TriggerAllFailed : EmergencyEvent
    data object TriggerNoContacts : EmergencyEvent
    data class TriggerCooldown(val remainingMs: Long) : EmergencyEvent
    data object TriggerEmptyBody : EmergencyEvent
    data object TriggerPanicSuppressed : EmergencyEvent
}

@HiltViewModel
class EmergencyViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val contactRepository: EmergencyContactRepository,
    private val triggerEmergency: TriggerEmergencyUseCase,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    /**
     * Single-flight guard — prevents the URGENCE button from firing a second `invoke` while the
     * first is still resolving GPS / dispatching SMS. Released in a `try/finally` so a thrown
     * exception cannot leave the guard latched.
     */
    private val triggerInFlight = AtomicBoolean(false)

    private val _events = Channel<EmergencyEvent>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<EmergencyUiState> = combine(
        settings.flow,
        contactRepository.observeAll(),
    ) { s, contacts ->
        val nowMono = SystemClock.elapsedRealtime()
        val lastMono = s.emergency.monotonicLastTriggeredAt
        val wallNow = System.currentTimeMillis()
        val lastWall = s.emergency.lastTriggeredAt
        val window = s.emergency.antiSpamWindowMs
        val inCooldown = (lastMono > 0L && (nowMono - lastMono) < window) ||
            (lastWall > 0L && (wallNow - lastWall) < window)
        EmergencyUiState(
            emergencyEnabled = s.emergency.enabled,
            callBehavior = s.security.emergencyCallBehavior,
            trustedContacts = contacts,
            canTrigger = !inCooldown && contacts.isNotEmpty(),
            voiceEnabled = s.voice.enabled,
            cascadeEnabled = s.cascade.enabled,
            sirenEnabled = s.emergency.sirenEnabled,
            liveGpsEnabled = s.liveGps.enabled,
            recordingEnabled = s.recording.enabled && s.recording.userAcknowledgedLegalDisclaimer,
            webhookEnabled = s.webhook.enabled && s.webhook.url.isNotBlank(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = EmergencyUiState(),
    )

    fun triggerEmergencySms() {
        if (!triggerInFlight.compareAndSet(false, true)) {
            Timber.w("EmergencyViewModel: trigger already in flight — ignoring tap")
            return
        }
        viewModelScope.launch(io) {
            try {
                // Flip enabled=true on first trigger so the lock-screen UX (notif, "Disable" button)
                // makes sense. Cooldown stamping is the use case's job — single source of truth.
                settings.update { s ->
                    if (s.emergency.enabled) s else s.copy(emergency = s.emergency.copy(enabled = true))
                }
                val event = when (val result = triggerEmergency()) {
                    TriggerEmergencyUseCase.Result.PanicSuppressed -> EmergencyEvent.TriggerPanicSuppressed
                    is TriggerEmergencyUseCase.Result.Cooldown -> EmergencyEvent.TriggerCooldown(result.remainingMs)
                    TriggerEmergencyUseCase.Result.NoContacts -> EmergencyEvent.TriggerNoContacts
                    TriggerEmergencyUseCase.Result.EmptyBody -> EmergencyEvent.TriggerEmptyBody
                    is TriggerEmergencyUseCase.Result.Triggered -> when {
                        result.sent == 0 -> EmergencyEvent.TriggerAllFailed
                        result.failed == 0 -> EmergencyEvent.TriggerSuccess(result.sent, result.hadLocation)
                        else -> EmergencyEvent.TriggerPartial(result.sent, result.failed)
                    }
                }
                _events.trySend(event)
            } finally {
                triggerInFlight.set(false)
            }
        }
    }

    fun disableEmergencyMode() {
        viewModelScope.launch(io) {
            settings.update { s -> s.copy(emergency = s.emergency.copy(enabled = false)) }
        }
    }

    fun triggerDryRun() {
        // TODO v0.3: EmergencyDryRunUseCase (no side effects, returns rendered body + redacted contacts).
        Timber.d("EmergencyViewModel: dry run (stub v0.2 — full impl v0.3)")
    }
}
