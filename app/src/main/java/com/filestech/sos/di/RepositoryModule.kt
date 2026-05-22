package com.filestech.sos.di

import com.filestech.sos.data.repository.EmergencyContactRepositoryImpl
import com.filestech.sos.domain.cascade.CascadeDialer
import com.filestech.sos.domain.contact.EmergencyContactRepository
import com.filestech.sos.domain.emergency.DefaultPanicGuard
import com.filestech.sos.domain.emergency.PanicGuard
import com.filestech.sos.domain.recording.RecordingController
import com.filestech.sos.domain.siren.SirenController
import com.filestech.sos.domain.webhook.WebhookDispatcher
import com.filestech.sos.system.service.CascadeDialerStub
import com.filestech.sos.system.service.RecordingControllerStub
import com.filestech.sos.system.service.SirenControllerStub
import com.filestech.sos.system.service.WebhookDispatcherStub
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindSirenController(impl: SirenControllerStub): SirenController

    @Binds @Singleton
    abstract fun bindCascadeDialer(impl: CascadeDialerStub): CascadeDialer

    @Binds @Singleton
    abstract fun bindRecordingController(impl: RecordingControllerStub): RecordingController

    @Binds @Singleton
    abstract fun bindWebhookDispatcher(impl: WebhookDispatcherStub): WebhookDispatcher

    @Binds @Singleton
    abstract fun bindEmergencyContactRepository(impl: EmergencyContactRepositoryImpl): EmergencyContactRepository

    // v0.2 stub: returns isPanicActive() == false. Wire to real PanicService in v0.3.
    @Binds @Singleton
    abstract fun bindPanicGuard(impl: DefaultPanicGuard): PanicGuard
}
