package com.filestech.sos.system.service

import com.filestech.sos.core.result.AppError
import com.filestech.sos.core.result.Outcome
import com.filestech.sos.domain.cascade.CascadeDialer
import com.filestech.sos.domain.recording.CallLogEntry
import com.filestech.sos.domain.recording.RecordingController
import com.filestech.sos.domain.siren.SirenController
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub implementations for features not yet backed by real code.
 * All return [Outcome.Failure] with [AppError.NotImplemented] so callers handle gracefully.
 *
 * WebhookDispatcherStub removed in v0.4 — replaced by [com.filestech.sos.data.webhook.WebhookDispatcherImpl].
 */

@Singleton
class SirenControllerStub @Inject constructor() : SirenController {
    private var active = false
    override suspend fun start() { active = true }
    override suspend fun stop() { active = false }
    override fun isActive(): Boolean = active
}

@Singleton
class CascadeDialerStub @Inject constructor() : CascadeDialer {
    private var running = false
    override suspend fun startCascade() { running = true }
    override suspend fun abortCascade() { running = false }
    override fun isRunning(): Boolean = running
}

@Singleton
class RecordingControllerStub @Inject constructor() : RecordingController {
    private var recording = false
    override suspend fun startRecording(): Outcome<Unit> =
        Outcome.Failure(AppError.NotImplemented("recording"))

    override suspend fun stopAndSave(): Outcome<CallLogEntry> =
        Outcome.Failure(AppError.NotImplemented("recording"))

    override fun isRecording(): Boolean = recording
}
