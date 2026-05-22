package com.filestech.sos.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.PhoneForwarded
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.Webhook
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filestech.sos.R
import com.filestech.sos.ui.theme.BrandDanger
import com.filestech.sos.ui.theme.BrandEmergency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToEmergency: () -> Unit,
    onNavigateToVoice: () -> Unit,
    onNavigateToCascade: () -> Unit,
    onNavigateToSiren: () -> Unit,
    onNavigateToLiveGps: () -> Unit,
    onNavigateToRecording: () -> Unit,
    onNavigateToWebhook: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                },
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Emergency armed chip
            if (state.emergencyEnabled) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Default.FiberManualRecord,
                                contentDescription = null,
                                tint = BrandDanger,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = stringResource(R.string.home_emergency_armed),
                                style = MaterialTheme.typography.labelLarge,
                                color = BrandDanger,
                            )
                        }
                    }
                }
            }

            // Main URGENCE button
            item {
                Button(
                    onClick = onNavigateToEmergency,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandEmergency,
                        contentColor = androidx.compose.ui.graphics.Color.White,
                    ),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.Default.Sos, contentDescription = null, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.home_emergency_button),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.home_features_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Feature cards
            item {
                FeatureCard(
                    icon = Icons.Default.Mic,
                    title = stringResource(R.string.feature_voice_title),
                    description = stringResource(R.string.feature_voice_desc),
                    enabled = state.voiceEnabled,
                    onClick = onNavigateToVoice,
                )
            }
            item {
                FeatureCard(
                    icon = Icons.AutoMirrored.Filled.PhoneForwarded,
                    title = stringResource(R.string.feature_cascade_title),
                    description = stringResource(R.string.feature_cascade_desc),
                    enabled = state.cascadeEnabled,
                    onClick = onNavigateToCascade,
                )
            }
            item {
                FeatureCard(
                    icon = Icons.Default.Campaign,
                    title = stringResource(R.string.feature_siren_title),
                    description = stringResource(R.string.feature_siren_desc),
                    enabled = state.sirenEnabled,
                    onClick = onNavigateToSiren,
                )
            }
            item {
                FeatureCard(
                    icon = Icons.Default.LocationOn,
                    title = stringResource(R.string.feature_livegps_title),
                    description = stringResource(R.string.feature_livegps_desc),
                    enabled = state.liveGpsEnabled,
                    onClick = onNavigateToLiveGps,
                )
            }
            item {
                FeatureCard(
                    icon = Icons.Default.FiberManualRecord,
                    title = stringResource(R.string.feature_recording_title),
                    description = stringResource(R.string.feature_recording_desc),
                    enabled = state.recordingEnabled,
                    onClick = onNavigateToRecording,
                )
            }
            item {
                FeatureCard(
                    icon = Icons.Default.Webhook,
                    title = stringResource(R.string.feature_webhook_title),
                    description = stringResource(R.string.feature_webhook_desc),
                    enabled = state.webhookEnabled,
                    onClick = onNavigateToWebhook,
                )
            }
            item {
                FeatureCard(
                    icon = Icons.Default.ContactPhone,
                    title = stringResource(R.string.feature_contacts_title),
                    description = stringResource(R.string.feature_contacts_desc),
                    enabled = state.contactsConfigured,
                    onClick = onNavigateToContacts,
                )
            }
        }
    }
}

@Composable
private fun FeatureCard(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (enabled) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = "ON",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
