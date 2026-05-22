package com.filestech.sos.di

import com.filestech.sos.domain.cascade.CascadeDialer
import com.filestech.sos.domain.recording.RecordingController
import com.filestech.sos.domain.siren.SirenController
import com.filestech.sos.domain.siren.SirenControllerStub
import com.filestech.sos.domain.webhook.WebhookDispatcher
import com.filestech.sos.system.service.CascadeDialerStub
import com.filestech.sos.system.service.RecordingControllerStub
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
}
