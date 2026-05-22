package com.filestech.sos.data.local.datastore

import com.filestech.sos.domain.cascade.CascadeConfig
import com.filestech.sos.domain.emergency.EmergencyCallBehavior
import com.filestech.sos.domain.livegps.LiveGpsConfig
import com.filestech.sos.domain.recording.RecordingConfig
import com.filestech.sos.domain.voice.VoiceTriggerConfig
import com.filestech.sos.domain.webhook.WebhookConfig
import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val appearance: Appearance = Appearance(),
    val security: SecuritySettings = SecuritySettings(),
    val emergency: EmergencySettings = EmergencySettings(),
    val voice: VoiceTriggerConfig = VoiceTriggerConfig(),
    val cascade: CascadeConfig = CascadeConfig(),
    val liveGps: LiveGpsConfig = LiveGpsConfig(),
    val recording: RecordingConfig = RecordingConfig(),
    val webhook: WebhookConfig = WebhookConfig(),
    val advanced: AdvancedSettings = AdvancedSettings(),
)

@Serializable
data class Appearance(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColors: Boolean = true,
    val amoledTrueBlack: Boolean = false,
)

enum class ThemeMode { SYSTEM, LIGHT, DARK, DARK_TECH }

@Serializable
data class SecuritySettings(
    val flagSecure: Boolean = true,
    val lockMode: LockMode = LockMode.OFF,
    val emergencyCallBehavior: EmergencyCallBehavior = EmergencyCallBehavior.HOLD_3S_DIRECT_CALL,
)

enum class LockMode { OFF, PIN, BIOMETRIC }

@Serializable
data class EmergencySettings(
    val enabled: Boolean = false,
    val shortcutNotifEnabled: Boolean = false,
    val sirenEnabled: Boolean = false,
    val sendIAmOkSmsOnReset: Boolean = true,
    /** Epoch ms of last trigger. 0 = never triggered. */
    val lastTriggeredAt: Long = 0L,
    /** Monotonic ms of last trigger (anti-clock-manipulation). */
    val monotonicLastTriggeredAt: Long = 0L,
    /** Anti-spam window in milliseconds. */
    val antiSpamWindowMs: Long = 60_000L,
)

@Serializable
data class AdvancedSettings(
    val keepAliveService: Boolean = false,
)
