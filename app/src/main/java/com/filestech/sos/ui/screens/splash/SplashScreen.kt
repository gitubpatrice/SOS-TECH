package com.filestech.sos.ui.screens.splash

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filestech.sos.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * First-launch welcome splash. Plays once after install. Auto-dismisses after
 * [AUTO_DISMISS_MS] (5 s), or sooner via tap / back. 100% Compose, no external dependency.
 *
 * **Dismiss safety** — a single [AtomicBoolean] gates the exit so tap, back, auto-dismiss, and
 * the "already shown" cold-start path all converge through [dismissOnce] without double-firing
 * [onFinished] or double-persisting the DataStore flag.
 *
 * **No splash flash for returning users** — [SplashViewModel.shouldShow] is shared with
 * `SharingStarted.Eagerly` so the first composition reads the persisted value before the first
 * frame paints. If `shouldShow` is already false, the composable returns immediately after
 * triggering [onFinished] (no markShown write — flag already true).
 */
@Composable
fun SplashScreen(
    onFinished: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    val shouldShow by viewModel.shouldShow.collectAsStateWithLifecycle()

    val firedGuard = remember(viewModel, onFinished) { AtomicBoolean(false) }
    val dismissOnce: () -> Unit = remember(viewModel, onFinished) {
        {
            if (firedGuard.compareAndSet(false, true)) {
                viewModel.markShown()
                onFinished()
            }
        }
    }

    if (!shouldShow) {
        LaunchedEffect(Unit) {
            if (firedGuard.compareAndSet(false, true)) {
                onFinished()
            }
        }
        return
    }

    val logoScale = remember { Animatable(initialValue = 0.5f) }
    val logoAlpha = remember { Animatable(initialValue = 0f) }
    val taglineAlpha = remember { Animatable(initialValue = 0f) }
    val hintAlpha = remember { Animatable(initialValue = 0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                logoAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = LOGO_ANIM_MS, easing = EaseOutCubic),
                )
            }
            launch {
                logoScale.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = LOGO_ANIM_MS, easing = EaseOutCubic),
                )
            }
            launch {
                delay(TAGLINE_DELAY_MS)
                taglineAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = TAGLINE_ANIM_MS, easing = EaseOutCubic),
                )
            }
            launch {
                delay(HINT_DELAY_MS)
                hintAlpha.animateTo(
                    targetValue = HINT_FINAL_ALPHA,
                    animationSpec = tween(durationMillis = HINT_ANIM_MS),
                )
            }
            launch {
                delay(AUTO_DISMISS_MS)
                dismissOnce()
            }
        }
    }

    BackHandler(enabled = true) { dismissOnce() }

    val interaction = remember { MutableInteractionSource() }
    val configuration = LocalConfiguration.current
    val logoSizeDp = (configuration.screenWidthDp * LOGO_SIZE_RATIO)
        .coerceIn(LOGO_MIN_DP, LOGO_MAX_DP).dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = stringResource(R.string.splash_skip_label),
                onClick = dismissOnce,
            )
            .clearAndSetSemantics { /* TalkBack announces named children only */ },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.sos_tech_icon),
                contentDescription = stringResource(R.string.splash_logo_content_description),
                modifier = Modifier
                    .size(logoSizeDp)
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value),
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.alpha(taglineAlpha.value),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.splash_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(taglineAlpha.value),
            )
        }

        Text(
            text = stringResource(R.string.splash_skip_hint),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .alpha(hintAlpha.value),
        )
    }
}

// Timings calibrated for ~5 s total. Tagline appears mid-animation for continuity; hint fades
// in late so it does not steal attention from the logo / tagline reveal.
private const val LOGO_ANIM_MS = 900
private const val TAGLINE_DELAY_MS = 700L
private const val TAGLINE_ANIM_MS = 800
private const val HINT_DELAY_MS = 2500L
private const val HINT_ANIM_MS = 500
private const val HINT_FINAL_ALPHA = 0.6f
private const val AUTO_DISMISS_MS = 5_000L
private const val LOGO_SIZE_RATIO = 0.4f
private const val LOGO_MIN_DP = 128f
private const val LOGO_MAX_DP = 200f
