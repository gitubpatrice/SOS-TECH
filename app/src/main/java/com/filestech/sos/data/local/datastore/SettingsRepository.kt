package com.filestech.sos.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.filestech.sos.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sos_settings")

private val KEY_SETTINGS = stringPreferencesKey("app_settings_v1")

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    val flow: Flow<AppSettings> = context.dataStore.data
        .map { prefs ->
            val raw = prefs[KEY_SETTINGS]
            if (raw == null) AppSettings()
            else runCatching { json.decodeFromString<AppSettings>(raw) }
                .onFailure { Timber.w(it, "SettingsRepository: corrupt prefs, using defaults") }
                .getOrDefault(AppSettings())
        }
        .onEach { _state.value = it }

    private val _state = MutableStateFlow(AppSettings())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    suspend fun update(transform: (AppSettings) -> AppSettings) = withContext(io) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_SETTINGS]
                ?.let { runCatching { json.decodeFromString<AppSettings>(it) }.getOrDefault(AppSettings()) }
                ?: AppSettings()
            val updated = transform(current)
            prefs[KEY_SETTINGS] = json.encodeToString(updated)
        }
    }
}
