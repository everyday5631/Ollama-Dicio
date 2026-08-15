package org.stypox.dicio.ui.enclave

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.stypox.dicio.R
import org.stypox.dicio.settings.datastore.Theme as SettingsTheme
import org.stypox.dicio.ui.theme.AppTheme
import org.stypox.dicio.ui.theme.EnclaveTokens

/**
 * Screen 1d — the single privacy-promise onboarding slide.
 *
 * [pageCount] and [currentPage] drive the progress dots so this slide can sit inside a larger
 * onboarding pager later without being rewritten.
 */
@Composable
fun OnboardingScreen(
    onGetStarted: () -> Unit,
    onReadPrivacyPromise: () -> Unit,
    modifier: Modifier = Modifier,
    pageCount: Int = 3,
    currentPage: Int = 0,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EnclaveTokens.Bg)
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.7f))

        // Image rather than Icon: Icon applies a flat tint, and the cube's three faces
        // must keep their own colours
        Image(
            painter = painterResource(R.drawable.ic_enclave_foreground),
            contentDescription = "Enclave",
            modifier = Modifier.size(132.dp),
        )

        Spacer(Modifier.height(28.dp))

        Text(
            "Your assistant.\nYour phone.\nNobody else.",
            style = MaterialTheme.typography.headlineLarge,
            color = EnclaveTokens.Text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Enclave listens, thinks and answers on your device. No account, no cloud, " +
                "no telemetry — and you decide which skills may reach the internet at all.",
            style = MaterialTheme.typography.bodyMedium,
            color = EnclaveTokens.TextMuted,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(30.dp))

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FeatureRow(
                Icons.Default.GraphicEq,
                "Offline speech",
                "Vosk turns your voice into text without a network.",
            )
            FeatureRow(
                Icons.Default.Memory,
                "Local LLM orchestrator",
                "A small model on your phone decides what to do.",
            )
            FeatureRow(
                Icons.Default.Tune,
                "You control every skill",
                "Each skill's internet access is yours to grant.",
            )
        }

        Spacer(Modifier.weight(1f))

        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(pageCount) { index ->
                val active = index == currentPage
                Box(
                    Modifier
                        .size(width = if (active) 20.dp else 7.dp, height = 7.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) EnclaveTokens.Accent
                            else EnclaveTokens.SurfaceVariant
                        )
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        EnclavePrimaryButton("Get started", onClick = onGetStarted)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onReadPrivacyPromise,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Read the privacy promise",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = EnclaveTokens.TextMuted,
            )
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, description: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, EnclaveTokens.Accent, size = 38.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = EnclaveTokens.Text)
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = EnclaveTokens.TextDim,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F0A1E, heightDp = 780)
@Composable
private fun OnboardingPreview() {
    AppTheme(theme = SettingsTheme.THEME_DARK) {
        OnboardingScreen(onGetStarted = {}, onReadPrivacyPromise = {})
    }
}
