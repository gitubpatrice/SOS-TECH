package com.filestech.sos.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filestech.sos.R
import com.filestech.sos.domain.emergency.EmergencyTemplate

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
            EmergencyTemplateRow(
                current = state.emergencyTemplate,
                onSelect = viewModel::selectTemplate,
            )
            SettingsToggleRow(
                title = stringResource(R.string.settings_emergency_include_location_title),
                subtitle = stringResource(R.string.settings_emergency_include_location_desc),
                checked = state.emergencyIncludeLocation,
                onToggle = { viewModel.toggleIncludeLocation(it) },
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

@Composable
private fun EmergencyTemplateRow(
    current: EmergencyTemplate,
    onSelect: (EmergencyTemplate) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val labelRes = current.labelRes
    ListItem(
        modifier = Modifier.clickable { open = true },
        headlineContent = { Text(stringResource(R.string.settings_emergency_template_title)) },
        supportingContent = {
            Text(
                stringResource(labelRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        },
    )
    if (open) {
        EmergencyTemplatePickerDialog(
            current = current,
            onCancel = { open = false },
            onConfirm = {
                onSelect(it)
                open = false
            },
        )
    }
}

@Composable
private fun EmergencyTemplatePickerDialog(
    current: EmergencyTemplate,
    onCancel: () -> Unit,
    onConfirm: (EmergencyTemplate) -> Unit,
) {
    var selected by remember(current) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.settings_emergency_template_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.settings_emergency_template_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                EmergencyTemplate.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == selected,
                                onClick = { selected = option },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(
                            text = stringResource(option.labelRes),
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
