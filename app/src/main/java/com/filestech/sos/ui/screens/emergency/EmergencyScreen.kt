package com.filestech.sos.ui.screens.emergency

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filestech.sos.R
import com.filestech.sos.domain.emergency.EmergencyCallBehavior
import com.filestech.sos.security.EmergencyCallHelper
import com.filestech.sos.ui.theme.BrandDanger
import com.filestech.sos.ui.theme.BrandEmergency

// Emergency number colors (WCAG AA vs White verified)
private val Color112 = Color(0xFFC62828)      // Red BrandDanger
private val Color15Samu = Color(0xFF00796B)   // Teal
private val Color17Police = Color(0xFF1565C0)  // Navy
private val Color18Pompiers = Color(0xFFE65100) // Orange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyScreen(
    onBack: () -> Unit,
    viewModel: EmergencyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.emergency_screen_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandEmergency,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Section 1: Appeler directement
            item {
                SectionTitle(stringResource(R.string.emergency_section_call_title))
            }
            item {
                EmergencyCallTile(
                    number = "112",
                    label = stringResource(R.string.emergency_call_112_label),
                    containerColor = Color112,
                    icon = Icons.Default.Sos,
                    behavior = state.callBehavior,
                    onClick = {
                        EmergencyCallHelper.placeEmergencyCall(context, "112", state.callBehavior)
                    },
                )
            }
            item {
                EmergencyCallTile(
                    number = "15",
                    label = stringResource(R.string.emergency_call_15_label),
                    containerColor = Color15Samu,
                    icon = Icons.Default.LocalHospital,
                    behavior = state.callBehavior,
                    onClick = {
                        EmergencyCallHelper.placeEmergencyCall(context, "15", state.callBehavior)
                    },
                )
            }
            item {
                EmergencyCallTile(
                    number = "17",
                    label = stringResource(R.string.emergency_call_17_label),
                    containerColor = Color17Police,
                    icon = Icons.Default.LocalPolice,
                    behavior = state.callBehavior,
                    onClick = {
                        EmergencyCallHelper.placeEmergencyCall(context, "17", state.callBehavior)
                    },
                )
            }
            item {
                EmergencyCallTile(
                    number = "18",
                    label = stringResource(R.string.emergency_call_18_label),
                    containerColor = Color18Pompiers,
                    icon = Icons.Default.Phone,
                    behavior = state.callBehavior,
                    onClick = {
                        EmergencyCallHelper.placeEmergencyCall(context, "18", state.callBehavior)
                    },
                )
            }
            // Trusted contact tile (shown if contacts configured)
            if (state.trustedContacts.isNotEmpty()) {
                item {
                    EmergencyCallTile(
                        number = "",
                        label = stringResource(R.string.emergency_call_close_label),
                        containerColor = MaterialTheme.colorScheme.primary,
                        icon = Icons.Default.Person,
                        behavior = state.callBehavior,
                        onClick = {
                            val contact = state.trustedContacts.firstOrNull()
                            if (contact != null) {
                                EmergencyCallHelper.placeTrustedContactCall(context, contact.phoneNumber)
                            }
                        },
                    )
                }
            }

            // Section 2: SMS URGENCE hold-3s
            item { SectionTitle(stringResource(R.string.emergency_section_sms_title)) }
            item {
                // TODO v0.2: UrgenceHoldButton with 3s progress indicator + EmergencyViewModel.trigger()
                Button(
                    onClick = { viewModel.triggerEmergencySms() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandEmergency,
                        contentColor = Color.White,
                    ),
                    enabled = state.canTrigger,
                ) {
                    Icon(Icons.Default.Sos, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.emergency_sms_trigger_button),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }

            // Active features recap
            if (state.activeFeaturesCount > 0) {
                item { SectionTitle(stringResource(R.string.emergency_section_options_title)) }
                item {
                    ActiveFeaturesRecap(state)
                }
            }

            // Section 3: Autres actions
            item { SectionTitle(stringResource(R.string.emergency_section_other_title)) }
            item {
                OutlinedButton(
                    onClick = { viewModel.triggerDryRun() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.emergency_dry_run_button))
                }
            }
            if (state.emergencyEnabled) {
                item {
                    OutlinedButton(
                        onClick = { viewModel.disableEmergencyMode() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = BrandDanger,
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BrandDanger),
                    ) {
                        Text(stringResource(R.string.emergency_disable_button))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EmergencyCallTile(
    number: String,
    label: String,
    containerColor: Color,
    icon: ImageVector,
    behavior: EmergencyCallBehavior,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = Color.White,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Column {
                if (number.isNotEmpty()) {
                    Text(number, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                }
                Text(label, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ActiveFeaturesRecap(state: EmergencyUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (state.voiceEnabled) {
            Text("• ${stringResource(R.string.feature_voice_title)}", style = MaterialTheme.typography.bodySmall)
        }
        if (state.cascadeEnabled) {
            Text("• ${stringResource(R.string.feature_cascade_title)}", style = MaterialTheme.typography.bodySmall)
        }
        if (state.sirenEnabled) {
            Text("• ${stringResource(R.string.feature_siren_title)}", style = MaterialTheme.typography.bodySmall)
        }
        if (state.liveGpsEnabled) {
            Text("• ${stringResource(R.string.feature_livegps_title)}", style = MaterialTheme.typography.bodySmall)
        }
        if (state.recordingEnabled) {
            Text("• ${stringResource(R.string.feature_recording_title)}", style = MaterialTheme.typography.bodySmall)
        }
        if (state.webhookEnabled) {
            Text("• ${stringResource(R.string.feature_webhook_title)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
