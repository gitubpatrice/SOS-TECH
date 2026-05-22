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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filestech.sos.R
import com.filestech.sos.data.local.datastore.LockMode
import com.filestech.sos.domain.emergency.EmergencyTemplate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showPinClearConfirm by remember { mutableStateOf(false) }
    var showPanicSetupDialog by remember { mutableStateOf(false) }
    var showPanicClearConfirm by remember { mutableStateOf(false) }

    val msgPinSet = stringResource(R.string.settings_pin_set_success)
    val msgPinCleared = stringResource(R.string.settings_pin_cleared)
    val msgPanicSet = stringResource(R.string.settings_panic_set_success)
    val msgPanicCleared = stringResource(R.string.settings_panic_cleared)
    val msgBioFailed = stringResource(R.string.settings_biometric_error_no_pin)
    // SEC-2: pre-resolved outside LaunchedEffect (stringResource requires Composition context)
    val msgPanicSameAsPin = stringResource(R.string.settings_panic_same_as_pin)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                SettingsEvent.PinSetSuccess -> snackbarHost.showSnackbar(msgPinSet)
                SettingsEvent.PinClearSuccess -> snackbarHost.showSnackbar(msgPinCleared)
                SettingsEvent.BiometricEnableSuccess -> { /* no snackbar needed */ }
                SettingsEvent.BiometricEnableFailed -> snackbarHost.showSnackbar(msgBioFailed)
                SettingsEvent.PanicSetSuccess -> snackbarHost.showSnackbar(msgPanicSet)
                SettingsEvent.PanicClearSuccess -> snackbarHost.showSnackbar(msgPanicCleared)
                SettingsEvent.NukeSuccess -> { /* handled elsewhere */ }
                // SEC-2: panic == PIN rejected — inform user with a clear message.
                SettingsEvent.PanicSameAsPin -> snackbarHost.showSnackbar(msgPanicSameAsPin)
                is SettingsEvent.Error -> snackbarHost.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
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
            // === Section Sécurité ===
            SettingsSectionHeader(stringResource(R.string.settings_section_security))

            ListItem(
                modifier = Modifier.clickable { showPinSetupDialog = true },
                headlineContent = { Text(stringResource(R.string.settings_pin_setup_title)) },
                supportingContent = {
                    Text(
                        if (state.isPinConfigured) stringResource(R.string.settings_pin_change)
                        else stringResource(R.string.settings_pin_setup_desc),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
            )
            if (state.isPinConfigured) {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_biometric_title),
                    subtitle = stringResource(R.string.settings_biometric_desc),
                    checked = state.lockMode == LockMode.BIOMETRIC,
                    onToggle = { enabled ->
                        if (enabled) viewModel.enableBiometric() else viewModel.disableBiometric()
                    },
                )
                ListItem(
                    modifier = Modifier.clickable { showPanicSetupDialog = true },
                    headlineContent = { Text(stringResource(R.string.settings_panic_title)) },
                    supportingContent = {
                        Text(
                            if (state.isPanicConfigured) stringResource(R.string.settings_panic_change)
                            else stringResource(R.string.settings_panic_desc),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                )
            }
            if (state.isPinConfigured) {
                ListItem(
                    modifier = Modifier.clickable { showPinClearConfirm = true },
                    headlineContent = {
                        Text(
                            stringResource(R.string.settings_pin_clear),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                )
            }

            HorizontalDivider()
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

    // Dialogs
    if (showPinSetupDialog) {
        PinSetupDialog(
            isChanging = state.isPinConfigured,
            onDismiss = { showPinSetupDialog = false },
            onConfirm = { pin ->
                viewModel.setPin(pin)
                showPinSetupDialog = false
            },
        )
    }

    if (showPinClearConfirm) {
        AlertDialog(
            onDismissRequest = { showPinClearConfirm = false },
            title = { Text(stringResource(R.string.settings_pin_clear_title)) },
            text = { Text(stringResource(R.string.settings_pin_clear_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearPin()
                        showPinClearConfirm = false
                    }
                ) {
                    Text(stringResource(R.string.action_disable), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinClearConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showPanicSetupDialog) {
        PanicSetupDialog(
            onDismiss = { showPanicSetupDialog = false },
            onConfirm = { pin ->
                viewModel.setPanicCode(pin)
                showPanicSetupDialog = false
            },
        )
    }

    if (showPanicClearConfirm) {
        AlertDialog(
            onDismissRequest = { showPanicClearConfirm = false },
            title = { Text(stringResource(R.string.settings_panic_clear_title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearPanicCode()
                        showPanicClearConfirm = false
                    }
                ) {
                    Text(stringResource(R.string.action_disable), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPanicClearConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

// ---- PIN setup dialog ----

@Composable
private fun PinSetupDialog(
    isChanging: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val mismatch = confirm.isNotEmpty() && pin != confirm
    val tooShort = pin.isNotEmpty() && pin.length < 4

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isChanging) stringResource(R.string.settings_pin_change_title)
                else stringResource(R.string.settings_pin_setup_title)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.lock_pin_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        autoCorrectEnabled = false,
                    ),
                    singleLine = true,
                    isError = tooShort,
                    supportingText = if (tooShort) {
                        { Text(stringResource(R.string.settings_pin_too_short)) }
                    } else null,
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.settings_pin_confirm_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        autoCorrectEnabled = false,
                    ),
                    singleLine = true,
                    isError = mismatch,
                    supportingText = if (mismatch) {
                        { Text(stringResource(R.string.settings_pin_mismatch)) }
                    } else null,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = pin.length >= 4 && pin == confirm,
                onClick = {
                    val array = pin.toCharArray()
                    pin = ""
                    confirm = ""
                    onConfirm(array)
                },
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

// ---- Panic code setup dialog ----

@Composable
private fun PanicSetupDialog(
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val mismatch = confirm.isNotEmpty() && pin != confirm
    val tooShort = pin.isNotEmpty() && pin.length < 4

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_panic_setup_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.settings_panic_setup_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.settings_panic_confirm_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        autoCorrectEnabled = false,
                    ),
                    singleLine = true,
                    isError = tooShort,
                    supportingText = if (tooShort) {
                        { Text(stringResource(R.string.settings_pin_too_short)) }
                    } else null,
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.settings_pin_confirm_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        autoCorrectEnabled = false,
                    ),
                    singleLine = true,
                    isError = mismatch,
                    supportingText = if (mismatch) {
                        { Text(stringResource(R.string.settings_pin_mismatch)) }
                    } else null,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = pin.length >= 4 && pin == confirm,
                onClick = {
                    val array = pin.toCharArray()
                    pin = ""
                    confirm = ""
                    onConfirm(array)
                },
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

// ---- Shared composables ----

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
