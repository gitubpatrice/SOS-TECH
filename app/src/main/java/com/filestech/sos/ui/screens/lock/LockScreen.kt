package com.filestech.sos.ui.screens.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filestech.sos.R
import com.filestech.sos.security.AppLockManager
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

@Composable
fun LockScreen(
    viewModel: LockViewModel = hiltViewModel(),
) {
    val lockState by viewModel.state.collectAsStateWithLifecycle()
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }

    // PIN field state — String for UI, converted to CharArray on submit
    var pin by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    // Countdown for locked-out state
    var countdownMs by remember { mutableLongStateOf(0L) }
    val isLockedOut by remember { derivedStateOf { lockState is AppLockManager.LockState.LockedOut } }

    val msgBiometricFailed = stringResource(R.string.lock_biometric_error)
    val msgInvalidPin = stringResource(R.string.lock_pin_invalid)

    LaunchedEffect(lockState) {
        val state = lockState
        // UI-2: reset isSubmitting on any state transition — covers success (Unlocked /
        // PanicDecoy) where the screen is about to disappear, and lockout transitions.
        isSubmitting = false
        if (state is AppLockManager.LockState.LockedOut) {
            countdownMs = state.until - System.currentTimeMillis()
            while (countdownMs > 0) {
                delay(1_000L)
                countdownMs = state.until - System.currentTimeMillis()
            }
        } else {
            countdownMs = 0L
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                LockEvent.InvalidPin -> {
                    pin = ""
                    isSubmitting = false
                    snackbarHost.showSnackbar(msgInvalidPin)
                }
                LockEvent.BiometricFailed -> {
                    snackbarHost.showSnackbar(msgBiometricFailed)
                }
            }
        }
    }

    // Auto-show biometric prompt if configured
    LaunchedEffect(isBiometricEnabled) {
        if (isBiometricEnabled && lockState is AppLockManager.LockState.Locked) {
            launchBiometricPrompt(context, viewModel)
        }
    }

    fun submitPin() {
        if (pin.isBlank() || isSubmitting || isLockedOut) return
        isSubmitting = true
        val pinArray = pin.toCharArray()
        pin = "" // clear UI field before async work
        // UI-2: no fixed 500 ms delay. isSubmitting is reset by:
        //  - LockEvent.InvalidPin handler (wrong PIN or lockout)
        //  - LaunchedEffect(lockState) below when state transitions (success / lockout)
        viewModel.attemptUnlock(pinArray)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Branding
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp),
                )
                Text(
                    text = stringResource(R.string.lock_screen_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(Modifier.height(8.dp))

                // PIN field
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (!isLockedOut) pin = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.lock_pin_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submitPin() }),
                    enabled = !isLockedOut && !isSubmitting,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Lockout countdown
                if (isLockedOut && countdownMs > 0) {
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(countdownMs) + 1L
                    Text(
                        text = stringResource(R.string.lock_locked_out, formatCountdown(seconds)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                // Unlock button
                Button(
                    onClick = { submitPin() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = pin.isNotEmpty() && !isLockedOut && !isSubmitting,
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.lock_unlock_button))
                    }
                }

                // Biometric button
                if (isBiometricEnabled && !isLockedOut) {
                    OutlinedButton(
                        onClick = { launchBiometricPrompt(context, viewModel) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.lock_biometric_button))
                    }
                }
            }
        }
    }
}

private fun formatCountdown(seconds: Long): String {
    return if (seconds >= 60) {
        val m = seconds / 60
        val s = seconds % 60
        "${m}m ${s}s"
    } else {
        "${seconds}s"
    }
}

private fun launchBiometricPrompt(context: Context, viewModel: LockViewModel) {
    val activity = context as? FragmentActivity ?: return
    val canAuth = BiometricManager.from(context).canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG
    )
    if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) return

    val challenge = viewModel.beginBiometricChallenge()

    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            viewModel.markBiometricUnlocked(challenge)
        }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            // User cancelled or hardware error — fall back to PIN silently
            viewModel.onBiometricError()
        }
        override fun onAuthenticationFailed() {
            // Biometric didn't match — let the user try PIN
        }
    }

    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(context), callback)
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(context.getString(R.string.lock_screen_title))
        .setNegativeButtonText(context.getString(R.string.lock_pin_label))
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .build()

    prompt.authenticate(promptInfo)
}
