package org.stypox.dicio.ui.enclave

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.stypox.dicio.settings.datastore.Theme as SettingsTheme
import org.stypox.dicio.ui.theme.AppTheme
import org.stypox.dicio.ui.theme.EnclaveTokens

/**
 * Screen 1g — privacy and data controls.
 *
 * "Work fully offline" is the master switch: when it is on, every skill's internet access is
 * blocked, so the individual "let skills reach the internet" toggle below it is disabled rather
 * than merely ignored — a toggle that silently does nothing is worse than one that visibly cannot
 * be used.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    state: PrivacyState,
    onChange: (PrivacyState) -> Unit,
    onClearAllData: () -> Unit,
    navigationIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = EnclaveTokens.Bg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Privacy & data",
                        style = MaterialTheme.typography.headlineMedium,
                        color = EnclaveTokens.Text,
                    )
                },
                navigationIcon = navigationIcon,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EnclaveTokens.Bg),
            )
        },
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StatusCard(cloudRequestsToday = state.cloudRequestsToday)

            EnclaveCard {
                EnclaveSettingRow(
                    title = "Work fully offline",
                    description = "Block every skill from reaching the internet.",
                    icon = Icons.Default.CloudOff,
                    onClick = { onChange(state.copy(offlineOnly = !state.offlineOnly)) },
                    trailing = {
                        EnclaveToggle(
                            checked = state.offlineOnly,
                            onCheckedChange = { onChange(state.copy(offlineOnly = it)) },
                        )
                    },
                )
                EnclaveDivider()
                EnclaveSettingRow(
                    title = "Wake word \"Hey Enclave\"",
                    description = "Listen in the background for the wake word.",
                    icon = Icons.Default.Hearing,
                    onClick = { onChange(state.copy(wakeWord = !state.wakeWord)) },
                    trailing = {
                        EnclaveToggle(
                            checked = state.wakeWord,
                            onCheckedChange = { onChange(state.copy(wakeWord = it)) },
                        )
                    },
                )
                EnclaveDivider()
                EnclaveSettingRow(
                    title = "Save conversation history",
                    description = "Keep past exchanges on this device.",
                    icon = Icons.Default.History,
                    onClick = { onChange(state.copy(saveHistory = !state.saveHistory)) },
                    trailing = {
                        EnclaveToggle(
                            checked = state.saveHistory,
                            onCheckedChange = { onChange(state.copy(saveHistory = it)) },
                        )
                    },
                )
                EnclaveDivider()
                EnclaveSettingRow(
                    title = "Let skills reach the internet",
                    description = if (state.offlineOnly) {
                        "Turned off while \"Work fully offline\" is on."
                    } else {
                        "Individual skills may make network requests."
                    },
                    icon = Icons.Default.Public,
                    iconTint = if (state.offlineOnly) EnclaveTokens.TextDim else EnclaveTokens.Warn,
                    onClick = if (state.offlineOnly) null else {
                        { onChange(state.copy(skillsNetwork = !state.skillsNetwork)) }
                    },
                    trailing = {
                        EnclaveToggle(
                            checked = state.skillsNetwork && !state.offlineOnly,
                            onCheckedChange = { onChange(state.copy(skillsNetwork = it)) },
                            enabled = !state.offlineOnly,
                        )
                    },
                )
                EnclaveDivider()
                EnclaveSettingRow(
                    title = "Anonymous crash logs",
                    description = "Share crash reports. Off by default.",
                    icon = Icons.Default.BugReport,
                    onClick = { onChange(state.copy(anonymousLogs = !state.anonymousLogs)) },
                    trailing = {
                        EnclaveToggle(
                            checked = state.anonymousLogs,
                            onCheckedChange = { onChange(state.copy(anonymousLogs = it)) },
                        )
                    },
                )
            }

            EnclaveOutlinedButton("Clear all data", onClick = { confirmClear = true }, danger = true)
            Text(
                "Clears models, conversation history and everything Enclave has learned. " +
                    "Uninstalling the app removes all of it too — nothing is stored anywhere else.",
                style = MaterialTheme.typography.labelSmall,
                color = EnclaveTokens.TextDim,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all data?") },
            text = {
                Text(
                    "This deletes downloaded models, conversation history and everything " +
                        "Enclave has learned about you. It cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClearAllData() }) {
                    Text("Clear everything", color = EnclaveTokens.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
            containerColor = EnclaveTokens.Surface,
        )
    }
}

/** The green "nothing has left your device" card. */
@Composable
private fun StatusCard(cloudRequestsToday: Int) {
    val clean = cloudRequestsToday == 0
    val color = if (clean) EnclaveTokens.Ok else EnclaveTokens.Warn

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(EnclaveTokens.RadiusCard))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(EnclaveTokens.RadiusCard))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.VerifiedUser, null, tint = color, modifier = Modifier.padding(2.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                if (clean) "Nothing has left your device" else "Some requests left your device",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                if (clean) {
                    "0 cloud requests today · 100% on-device"
                } else {
                    "$cloudRequestsToday cloud request(s) today, from skills you allowed"
                },
                style = MaterialTheme.typography.labelSmall,
                color = EnclaveTokens.TextMuted,
            )
        }
    }
}

/** The privacy flags this screen edits. */
data class PrivacyState(
    val offlineOnly: Boolean = false,
    val wakeWord: Boolean = true,
    val saveHistory: Boolean = true,
    val skillsNetwork: Boolean = true,
    val anonymousLogs: Boolean = false,
    val cloudRequestsToday: Int = 0,
)

@Preview(showBackground = true, backgroundColor = 0xFF0F0A1E, heightDp = 800)
@Composable
private fun PrivacyPreview() {
    AppTheme(theme = SettingsTheme.THEME_DARK) {
        var state by remember { mutableStateOf(PrivacyState()) }
        PrivacyScreen(
            state = state,
            onChange = { state = it },
            onClearAllData = {},
            navigationIcon = {},
        )
    }
}
