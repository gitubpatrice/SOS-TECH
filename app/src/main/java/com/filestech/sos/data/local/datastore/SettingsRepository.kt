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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

/**
 * Single source of truth for [AppSettings]. Exposes a [StateFlow] shared eagerly at Singleton
 * scope — a single DataStore subscription is multiplexed to N collectors with zero re-parsing.
 *
 * Fix B (v0.3.2): replaced cold [kotlinx.coroutines.flow.Flow] + redundant `_state`
 * MutableStateFlow+onEach pattern with a single `stateIn(Eagerly)`. Any caller that previously
 * read `settings.state.value` synchronously (acceptable because `Eagerly` guarantees the initial
 * emit is synchronous from the upstream cold flow on subscription) now reads the same
 * `settings.flow.value` directly.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + io)

    val flow: StateFlow<AppSettings> = context.dataStore.data
        .map { prefs ->
            val raw = prefs[KEY_SETTINGS]
            if (raw == null) AppSettings()
            else runCatching { json.decodeFromString<AppSettings>(raw) }
                .onFailure { Timber.w(it, "SettingsRepository: corrupt prefs, using defaults") }
                .getOrDefault(AppSettings())
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings(),
        )

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
