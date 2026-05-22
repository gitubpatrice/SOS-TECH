package com.filestech.sos

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.filestech.sos.data.local.datastore.AppSettings
import com.filestech.sos.data.local.datastore.SettingsRepository
import com.filestech.sos.ui.AppRoot
import com.filestech.sos.ui.theme.SosTechTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var settings: SettingsRepository

    private val initialSettings = MutableStateFlow<AppSettings?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FLAG_SECURE applied unconditionally as safe-default (privacy-preserving at cold-start).
        // Cleared once DataStore confirms user opted out.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        enableEdgeToEdge()

        lifecycleScope.launch {
            initialSettings.value = settings.flow.first()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settings.flow
                    .map { it.security.flagSecure }
                    .distinctUntilChanged()
                    .collect { applyFlagSecure(it) }
            }
        }

        setContent {
            val seed = initialSettings.collectAsStateWithLifecycle().value ?: AppSettings()
            val current by settings.flow.collectAsStateWithLifecycle(initialValue = seed)
            SosTechTheme(appearance = current.appearance) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }

    private fun applyFlagSecure(enabled: Boolean) {
        if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
