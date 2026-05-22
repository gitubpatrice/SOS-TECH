package com.filestech.sos.di

import android.content.Context
import androidx.room.Room
import com.filestech.sos.data.local.db.AppDatabase
import com.filestech.sos.data.local.db.dao.EmergencyContactDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        // TODO v0.2: derive passphrase from Android Keystore alias "sos_db_master"
        // using PasswordKdf + AES-GCM wrap (same pattern as SMS Tech SecurityStore).
        // v0.1: fixed passphrase placeholder — REPLACE before any production data.
        System.loadLibrary("sqlcipher")
        val passphraseBytes = "sos_db_placeholder_v01".toByteArray(Charsets.UTF_8)
        val factory = SupportOpenHelperFactory(passphraseBytes)

        return Room.databaseBuilder(context, AppDatabase::class.java, "sos_tech.db")
            .openHelperFactory(factory)
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true) // TODO: replace with proper Migration once first schema change occurs
            .build()
    }

    @Provides
    fun provideEmergencyContactDao(db: AppDatabase): EmergencyContactDao =
        db.emergencyContactDao()
}
