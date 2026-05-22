package com.filestech.sos.di

import android.content.Context
import com.filestech.sos.data.local.db.AppDatabase
import com.filestech.sos.data.local.db.DatabaseFactory
import com.filestech.sos.data.local.db.dao.EmergencyContactDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the SQLCipher-backed [AppDatabase].
 *
 * Crypto: the raw passphrase is a 32-byte random key wrapped by the AndroidKeyStore alias
 * `sostech_db_master`. See [DatabaseFactory] / [com.filestech.sos.data.local.db.DatabaseKeyManager]
 * for the derivation, persistence (`<files>/db/master.key`), and failure-handling contract.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        factory: DatabaseFactory,
    ): AppDatabase = factory.build(context)

    @Provides
    fun provideEmergencyContactDao(db: AppDatabase): EmergencyContactDao =
        db.emergencyContactDao()
}
