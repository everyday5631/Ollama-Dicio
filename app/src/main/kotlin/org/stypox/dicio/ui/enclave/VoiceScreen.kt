package org.stypox.dicio.ui.enclave

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.stypox.dicio.settings.datastore.Theme as SettingsTheme
import org.stypox.dicio.ui.theme.AppTheme
import org.stypox.dicio.ui.theme.EnclaveTokens

/**
 * Screen 1a — the voice hero: a glowing orb that pulses while idle and grows a waveform while
 * listening, over a wordmark, an "On-device" badge, and the live transcript.
 *
 * This is presentation only: [listening], [partialTranscript] and the callbacks are supplied by the
 * caller, so the same composable serves the real STT flow and the previews below without the screen
 * needing to know about Vosk or the orchestrator.
 */
@Composable
fun VoiceScreen(
    listening: Boolean,
    partialTranscript: String,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier,
    onKeyboardClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EnclaveTokens.Bg)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Enclave",
                style = MaterialTheme.typography.displaySmall,
                color = EnclaveTokens.Text,
            )
            StatusPill("On-device", EnclaveTokens.Ok)
        }

        Spacer(Modifier.weight(1f))

        Orb(listening = listening)

        Spacer(Modifier.height(28.dp))

        Text(
            if (listening) "Listening…" else "Tap to speak",
            style = MaterialTheme.typography.headlineSmall,
            color = EnclaveTokens.Text,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Everything you say is processed on this phone.",
            style = MaterialTheme.typography.labelMedium,
            color = EnclaveTokens.TextDim,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(18.dp))

        // the live transcript keeps its slot whether or not there is text, so the orb and the
        // buttons do not jump around as words arrive
        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
            if (partialTranscript.isNotBlank()) {
                Text(
                    partialTranscript,
                    style = MaterialTheme.typography.bodyLarge,
                    color = EnclaveTokens.TextMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(Icons.Default.Keyboard, "Type instead", onKeyboardClick)
            Spacer(Modifier.width(28.dp))
            MicButton(listening = listening, onClick = onMicClick)
            Spacer(Modifier.width(28.dp))
            CircleIconButton(Icons.Outlined.History, "History", onHistoryClick)
        }
    }
}

/**
 * The orb: a radial-gradient sphere with an outer glow that pulses 1 → 1.14 over 3.4s. While
 * listening, five white bars scale vertically inside it.
 */
@Composable
private fun Orb(listening: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(EnclaveTokens.OrbPulseMs),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orbPulse",
    )

    Box(modifier = modifier.size(240.dp), contentAlignment = Alignment.Center) {
        // outer glow: the same gradient, faded and scaled by the pulse
        Box(
            Modifier
                .size(200.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(EnclaveTokens.Accent.copy(alpha = 0.16f))
        )
        Box(
            Modifier
                .size(168.dp)
                .clip(CircleShape)
                .background(EnclaveTokens.OrbGradient),
            contentAlignment = Alignment.Center,
        ) {
            if (listening) {
                Waveform(
                    barCount = 5,
                    barWidth = 7.dp,
                    maxHeight = 62.dp,
                    color = Color.White,
                )
            }
        }
    }
}

/**
 * Vertically scaling bars, staggered so the row reads as a travelling wave.
 *
 * Each bar gets its own infinite transition offset by a fraction of the period; using one
 * transition with per-bar phase would need a keyframe spec per bar for no visual gain.
 */
@Composable
fun Waveform(
    barCount: Int,
    barWidth: androidx.compose.ui.unit.Dp,
    maxHeight: androidx.compose.ui.unit.Dp,
    color: Color,
    modifier: Modifier = Modifier,
    gradient: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "waveform")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(barWidth * 0.6f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(barCount) { index ->
            // spread the bars evenly across the period so neighbours are always out of phase
            val delay = (EnclaveTokens.WaveformMs / barCount) * index
            val scale by transition.animateFloat(
                initialValue = 0.32f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(EnclaveTokens.WaveformMs, delayMillis = delay),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bar$index",
            )
            Box(
                Modifier
                    .width(barWidth)
                    .height(maxHeight * scale)
                    .clip(RoundedCornerShape(barWidth / 2))
                    .then(
                        if (gradient) Modifier.background(EnclaveTokens.AccentGradient)
                        else Modifier.background(color)
                    )
            )
        }
    }
}

/** The 78dp accent-gradient mic FAB. */
@Composable
private fun MicButton(listening: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(EnclaveTokens.MicHero)
            .clip(CircleShape)
            .background(EnclaveTokens.AccentGradient)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (listening) Icons.Default.MicOff else Icons.Default.Mic,
            contentDescription = if (listening) "Stop listening" else "Start listening",
            tint = Color.White,
            modifier = Modifier.size(32.dp),
        )
    }
}

@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(EnclaveTokens.SurfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = EnclaveTokens.TextMuted,
            modifier = Modifier.size(21.dp))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F0A1E, heightDp = 700)
@Composable
private fun VoiceScreenIdlePreview() {
    AppTheme(theme = SettingsTheme.THEME_DARK) {
        VoiceScreen(listening = false, partialTranscript = "", onMicClick = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F0A1E, heightDp = 700)
@Composable
private fun VoiceScreenListeningPreview() {
    AppTheme(theme = SettingsTheme.THEME_DARK) {
        VoiceScreen(
            listening = true,
            partialTranscript = "what's the weather like tomorrow",
            onMicClick = {},
        )
    }
}
