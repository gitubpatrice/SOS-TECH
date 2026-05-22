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
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
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
        val passphraseBytes = SQLiteDatabase.getBytes("sos_db_placeholder_v01".toCharArray())
        val factory = SupportFactory(passphraseBytes)

        return Room.databaseBuilder(context, AppDatabase::class.java, "sos_tech.db")
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration() // TODO: remove once first migration is defined
            .build()
    }

    @Provides
    fun provideEmergencyContactDao(db: AppDatabase): EmergencyContactDao =
        db.emergencyContactDao()
}
