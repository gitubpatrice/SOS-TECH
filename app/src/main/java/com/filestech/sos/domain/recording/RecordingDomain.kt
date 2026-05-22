package com.filestech.sos.domain.recording

import kotlinx.serialization.Serializable

/**
 * AVERTISSEMENT LEGAL FRANCE — OBLIGATOIRE :
 *
 * L'enregistrement audio d'un appel téléphonique sans le consentement préalable de tous les
 * interlocuteurs est punissable en France par l'article 226-15 du Code pénal
 * (jusqu'à 1 an d'emprisonnement + 45 000 € d'amende). Ce disclaimer DOIT être affiché
 * au premier opt-in de l'utilisateur et rappelé à chaque démarrage d'enregistrement.
 *
 * Seul un enregistrement AVEC consentement explicite de toutes les parties est légal.
 * SOS Tech ne peut pas contrôler que l'utilisateur a obtenu ce consentement —
 * la responsabilité repose entièrement sur l'utilisateur.
 *
 * This disclaimer is displayed via [RecordingDisclaimerContent] composable before any
 * opt-in toggle is accepted. The flag [RecordingConfig.userAcknowledgedLegalDisclaimer]
 * must be true before recording can be enabled.
 */
const val RECORDING_LEGAL_DISCLAIMER_FR = """
AVERTISSEMENT LÉGAL — ENREGISTREMENT D'APPEL

En France, enregistrer un appel téléphonique sans le consentement préalable de tous
les interlocuteurs est une infraction pénale (art. 226-15 du Code pénal).

En activant cette fonctionnalité, vous certifiez avoir obtenu le consentement explicite
de toutes les personnes qui pourraient être enregistrées.

SOS Tech ne stocke les enregistrements que localement, chiffrés dans un coffre sécurisé.
Aucune donnée n'est transmise à un serveur externe.

La responsabilité légale incombe entièrement à l'utilisateur.
"""

/** Vault SQLCipher Keystore alias for the recording encryption key. */
const val RECORDING_VAULT_KEYSTORE_ALIAS = "sos_recording_kek"

/** Maximum duration of a single ambient recording in milliseconds. */
const val RECORDING_MAX_DURATION_MS = 30 * 1_000L // 30 seconds

@Serializable
data class RecordingConfig(
    val enabled: Boolean = false,
    /** User has read and accepted the legal disclaimer. Must be true before enabling. */
    val userAcknowledgedLegalDisclaimer: Boolean = false,
    /** Keep recordings in vault after emergency session ends. */
    val retainInVault: Boolean = true,
)

/**
 * A single recording session log entry.
 * Stored in the SQLCipher vault regardless of whether audio recording is enabled.
 */
@Serializable
data class CallLogEntry(
    val id: Long = 0L,
    val timestampMs: Long,
    val durationMs: Long,
    val phoneNumber: String,
    val outcome: CallOutcome,
    /** SAF URI or Room entity ID of the encrypted audio file, null if recording was off. */
    val encryptedAudioRef: String? = null,
)

enum class CallOutcome {
    ANSWERED,
    NOT_ANSWERED,
    BUSY,
    FAILED,
}

interface RecordingController {
    /**
     * Start an ambient microphone recording.
     * Auto-stops after [RECORDING_MAX_DURATION_MS].
     * Requires RECORD_AUDIO permission.
     */
    suspend fun startRecording(): com.filestech.sos.core.result.Outcome<Unit>

    /** Stop the current recording and encrypt into the vault. */
    suspend fun stopAndSave(): com.filestech.sos.core.result.Outcome<CallLogEntry>

    fun isRecording(): Boolean
}
