package com.filestech.sos.domain.voice

import kotlinx.serialization.Serializable

/** Action triggered when a voice keyword is recognized. */
enum class VoiceKeywordAction {
    TRIGGER_FULL,   // Full emergency: SMS + call cascade + siren + recording
    DIAL_112,
    DIAL_POLICE,    // 17
    DIAL_SAMU,      // 15
    DIAL_POMPIERS,  // 18
}

/** A keyword phrase + its associated action. */
@Serializable
data class VoiceKeyword(
    val phrase: String,
    val action: VoiceKeywordAction = VoiceKeywordAction.TRIGGER_FULL,
)

/**
 * Full configuration for the voice keyword trigger feature.
 *
 * [voskModelPath] is a SAF URI string pointing to the user-downloaded Vosk model (~50 MB).
 * The model is NOT bundled — user downloads it via the setup wizard (v0.2+).
 */
@Serializable
data class VoiceTriggerConfig(
    val enabled: Boolean = false,
    val keywords: List<VoiceKeyword> = listOf(
        VoiceKeyword("au secours", VoiceKeywordAction.TRIGGER_FULL),
        VoiceKeyword("appelle le 112", VoiceKeywordAction.DIAL_112),
    ),
    /** SAF URI string — null until user picks the model via the setup wizard. */
    val voskModelPath: String? = null,
    /** Number of keyword repetitions required before trigger fires. Anti-false-positive. */
    val repetitionCount: Int = 3,
    /** Minimum milliseconds between two voice triggers. */
    val cooldownMs: Long = 120_000L,
)
