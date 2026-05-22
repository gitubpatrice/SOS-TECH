package com.filestech.sos.ui.screens.emergency

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.sos.data.local.datastore.SettingsRepository
import com.filestech.sos.di.IoDispatcher
import com.filestech.sos.domain.contact.EmergencyContact
import com.filestech.sos.domain.emergency.EmergencyCallBehavior
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
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

@HiltViewModel
class EmergencyViewModel @Inject constructor(
    private val settings: SettingsRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    /** Single-flight guard — prevents double-trigger during concurrent call setup. */
    private val triggerInFlight = AtomicBoolean(false)

    val state: StateFlow<EmergencyUiState> = settings.flow
        .map { s ->
            val nowMono = SystemClock.elapsedRealtime()
            val lastMono = s.emergency.monotonicLastTriggeredAt
            val wallNow = System.currentTimeMillis()
            val lastWall = s.emergency.lastTriggeredAt
            val inCooldown = (lastMono > 0L && (nowMono - lastMono) < s.emergency.antiSpamWindowMs) ||
                (lastWall > 0L && (wallNow - lastWall) < s.emergency.antiSpamWindowMs)
            EmergencyUiState(
                emergencyEnabled = s.emergency.enabled,
                callBehavior = s.security.emergencyCallBehavior,
                canTrigger = !inCooldown,
                voiceEnabled = s.voice.enabled,
                cascadeEnabled = s.cascade.enabled,
                sirenEnabled = s.emergency.sirenEnabled,
                liveGpsEnabled = s.liveGps.enabled,
                recordingEnabled = s.recording.enabled && s.recording.userAcknowledgedLegalDisclaimer,
                webhookEnabled = s.webhook.enabled && s.webhook.url.isNotBlank(),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = EmergencyUiState(),
        )

    fun triggerEmergencySms() {
        if (!triggerInFlight.compareAndSet(false, true)) {
            Timber.w("EmergencyViewModel: trigger already in flight")
            return
        }
        viewModelScope.launch(io) {
            try {
                val nowWall = System.currentTimeMillis()
                val nowMono = SystemClock.elapsedRealtime()
                settings.update { s ->
                    s.copy(
                        emergency = s.emergency.copy(
                            enabled = true,
                            lastTriggeredAt = nowWall,
                            monotonicLastTriggeredAt = nowMono,
                        ),
                    )
                }
                // TODO v0.2: TriggerEmergencyUseCase — send SMS + start cascade + siren + GPS live + recording + webhook
                Timber.i("EmergencyViewModel: emergency triggered (stub v0.1)")
            } finally {
                triggerInFlight.set(false)
            }
        }
    }

    fun disableEmergencyMode() {
        viewModelScope.launch(io) {
            settings.update { s ->
                s.copy(emergency = s.emergency.copy(enabled = false))
            }
        }
    }

    fun triggerDryRun() {
        // TODO v0.2: EmergencyDryRunUseCase — no side effects, returns preview
        Timber.d("EmergencyViewModel: dry run (stub v0.1)")
    }
}
