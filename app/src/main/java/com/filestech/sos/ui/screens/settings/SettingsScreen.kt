package com.filestech.sos.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filestech.sos.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsSectionHeader(stringResource(R.string.settings_section_emergency))
            SettingsToggleRow(
                title = stringResource(R.string.settings_emergency_shortcut_title),
                subtitle = stringResource(R.string.settings_emergency_shortcut_desc),
                checked = state.shortcutNotifEnabled,
                onToggle = { viewModel.toggleEmergencyShortcut(it) },
            )

            HorizontalDivider()
            SettingsSectionHeader(stringResource(R.string.settings_section_voice))
            SettingsToggleRow(
                title = stringResource(R.string.settings_voice_enabled_title),
                subtitle = stringResource(R.string.settings_voice_enabled_desc),
                checked = state.voiceEnabled,
                onToggle = { viewModel.toggleVoice(it) },
            )

            HorizontalDivider()
            SettingsSectionHeader(stringResource(R.string.settings_section_cascade))
            SettingsToggleRow(
                title = stringResource(R.string.settings_cascade_enabled_title),
                subtitle = stringResource(R.string.settings_cascade_enabled_desc),
                checked = state.cascadeEnabled,
                onToggle = { viewModel.toggleCascade(it) },
            )

            HorizontalDivider()
            SettingsSectionHeader(stringResource(R.string.settings_section_siren))
            SettingsToggleRow(
                title = stringResource(R.string.settings_siren_enabled_title),
                subtitle = stringResource(R.string.settings_siren_enabled_desc),
                checked = state.sirenEnabled,
                onToggle = { viewModel.toggleSiren(it) },
            )

            HorizontalDivider()
            SettingsSectionHeader(stringResource(R.string.settings_section_livegps))
            SettingsToggleRow(
                title = stringResource(R.string.settings_livegps_enabled_title),
                subtitle = stringResource(R.string.settings_livegps_enabled_desc),
                checked = state.liveGpsEnabled,
                onToggle = { viewModel.toggleLiveGps(it) },
            )

            HorizontalDivider()
            SettingsSectionHeader(stringResource(R.string.settings_section_recording))
            // Disclaimer always visible in this section
            Text(
                text = stringResource(R.string.settings_recording_legal_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            SettingsToggleRow(
                title = stringResource(R.string.settings_recording_enabled_title),
                subtitle = stringResource(R.string.settings_recording_enabled_desc),
                checked = state.recordingEnabled,
                onToggle = { viewModel.toggleRecording(it) },
            )

            HorizontalDivider()
            SettingsSectionHeader(stringResource(R.string.settings_section_webhook))
            SettingsToggleRow(
                title = stringResource(R.string.settings_webhook_enabled_title),
                subtitle = stringResource(R.string.settings_webhook_enabled_desc),
                checked = state.webhookEnabled,
                onToggle = { viewModel.toggleWebhook(it) },
            )

            HorizontalDivider()
            SettingsSectionHeader(stringResource(R.string.settings_section_appearance))
            SettingsToggleRow(
                title = stringResource(R.string.settings_flag_secure_title),
                subtitle = stringResource(R.string.settings_flag_secure_desc),
                checked = state.flagSecure,
                onToggle = { viewModel.toggleFlagSecure(it) },
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onToggle)
        },
    )
}
