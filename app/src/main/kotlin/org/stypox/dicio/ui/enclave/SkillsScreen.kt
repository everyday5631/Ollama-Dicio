package org.stypox.dicio.ui.enclave

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.stypox.dicio.settings.datastore.Theme as SettingsTheme
import org.stypox.dicio.ui.theme.AppTheme
import org.stypox.dicio.ui.theme.EnclaveTokens

/**
 * Screen 1e — skills and plugins, split by whether a skill can work without a network.
 *
 * That split is the point of the screen: it makes the privacy consequence of each toggle legible
 * before it is flipped, which is what `PRIVACY.md`'s per-skill internet control is for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(
    skills: List<SkillRow>,
    onToggle: (String, Boolean) -> Unit,
    navigationIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }

    val matching = skills.filter {
        query.isBlank() || it.name.contains(query, ignoreCase = true) ||
            it.description.contains(query, ignoreCase = true)
    }
    val offline = matching.filter { !it.needsInternet }
    val online = matching.filter { it.needsInternet }

    Scaffold(
        containerColor = EnclaveTokens.Bg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Skills",
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search skills", color = EnclaveTokens.TextDim) },
                leadingIcon = {
                    Icon(Icons.Default.Search, null, tint = EnclaveTokens.TextDim)
                },
                singleLine = true,
                shape = RoundedCornerShape(EnclaveTokens.RadiusField),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = EnclaveTokens.Surface,
                    unfocusedContainerColor = EnclaveTokens.Surface,
                    focusedTextColor = EnclaveTokens.Text,
                    unfocusedTextColor = EnclaveTokens.Text,
                    focusedIndicatorColor = EnclaveTokens.Accent,
                    unfocusedIndicatorColor = EnclaveTokens.Line,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            if (offline.isNotEmpty()) {
                SectionHeader("Works offline", Icons.Default.CheckCircle, EnclaveTokens.Ok)
                SkillGroup(offline, onToggle)
            }

            if (online.isNotEmpty()) {
                SectionHeader("Needs internet", Icons.Default.Public, EnclaveTokens.Warn)
                SkillGroup(online, onToggle)
            }

            if (matching.isEmpty()) {
                Text(
                    "No skill matches \"$query\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EnclaveTokens.TextDim,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun SkillGroup(skills: List<SkillRow>, onToggle: (String, Boolean) -> Unit) {
    EnclaveCard {
        skills.forEachIndexed { index, skill ->
            if (index > 0) EnclaveDivider()
            EnclaveSettingRow(
                title = skill.name,
                description = skill.description,
                icon = skill.icon,
                iconTint = if (skill.needsInternet) EnclaveTokens.Warn else EnclaveTokens.Accent,
                tag = if (skill.needsInternet) {
                    { StatusPill("online", EnclaveTokens.Warn) }
                } else {
                    null
                },
                onClick = { onToggle(skill.id, !skill.enabled) },
                trailing = {
                    EnclaveToggle(
                        checked = skill.enabled,
                        onCheckedChange = { onToggle(skill.id, it) },
                    )
                },
            )
        }
    }
}

/** One row on the skills screen. */
data class SkillRow(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val needsInternet: Boolean,
    val enabled: Boolean,
)

@Preview(showBackground = true, backgroundColor = 0xFF0F0A1E, heightDp = 760)
@Composable
private fun SkillsPreview() {
    AppTheme(theme = SettingsTheme.THEME_DARK) {
        SkillsScreen(
            skills = listOf(
                SkillRow("timer", "Timer", "Set, query and cancel timers.",
                    Icons.Default.CheckCircle, needsInternet = false, enabled = true),
                SkillRow("calculator", "Calculator", "Evaluate basic calculations.",
                    Icons.Default.CheckCircle, needsInternet = false, enabled = true),
                SkillRow("weather", "Weather", "OpenWeatherMap.",
                    Icons.Default.Public, needsInternet = true, enabled = false),
            ),
            onToggle = { _, _ -> },
            navigationIcon = {},
        )
    }
}

/**
 * The skill catalogue from the repository README, rendered with the offline/online split the
 * design calls for.
 *
 * The `enabled` values here are placeholders: wiring each row to its real per-skill setting needs
 * `SkillSettingsViewModel` to expose an enabled flag keyed by skill id, which it does not do yet.
 * The split itself is real — it follows each skill's actual network use as documented in
 * `PRIVACY.md`.
 */
fun defaultSkillRows(): List<SkillRow> = listOf(
    SkillRow("timer", "Timer", "Set, query and cancel timers.",
        Icons.Default.Timer, needsInternet = false, enabled = true),
    SkillRow("calculator", "Calculator", "Evaluate basic calculations.",
        Icons.Default.Calculate, needsInternet = false, enabled = true),
    SkillRow("flashlight", "Flashlight", "Turn the phone flashlight on and off.",
        Icons.Default.FlashlightOn, needsInternet = false, enabled = true),
    SkillRow("current_time", "Current time", "Tell the time and date.",
        Icons.Default.Schedule, needsInternet = false, enabled = true),
    SkillRow("open", "Open app", "Launch an app on your device.",
        Icons.Default.Apps, needsInternet = false, enabled = true),
    SkillRow("media", "Media control", "Play, pause, previous and next.",
        Icons.Default.PlayArrow, needsInternet = false, enabled = true),
    SkillRow("weather", "Weather", "OpenWeatherMap.",
        Icons.Default.WbSunny, needsInternet = true, enabled = false),
    SkillRow("search", "Web search", "DuckDuckGo.",
        Icons.Default.Search, needsInternet = true, enabled = false),
    SkillRow("translation", "Translation", "Lingva.",
        Icons.Default.Translate, needsInternet = true, enabled = false),
    SkillRow("lyrics", "Lyrics", "Genius.",
        Icons.Default.MusicNote, needsInternet = true, enabled = false),
)
