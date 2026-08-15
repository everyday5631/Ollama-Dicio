package org.stypox.dicio.ui.enclave

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.stypox.dicio.llm.GgufModelManager
import org.stypox.dicio.llm.LlmModelState
import org.stypox.dicio.settings.LocalAiViewModel
import org.stypox.dicio.settings.datastore.Theme as SettingsTheme
import org.stypox.dicio.ui.theme.AppTheme
import org.stypox.dicio.ui.theme.EnclaveTokens

/**
 * Screen 1f — the on-device model manager.
 *
 * Everything here is driven from real state: [LlmModelState] for the active model's status and
 * download progress, and the settings store for the configured model reference. The catalogue below
 * lists the three models the handoff calls out, but which of them is *active* and how far a download
 * has got are read from the manager rather than mocked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerScreen(
    navigationIcon: @Composable () -> Unit,
    viewModel: LocalAiViewModel = hiltViewModel(),
) {
    val settings by viewModel.settingsState.collectAsState()
    val modelState by viewModel.modelState.collectAsState()
    val configured = settings.llmModelUrl.ifBlank { GgufModelManager.defaultModelUrl }

    Scaffold(
        containerColor = EnclaveTokens.Bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Eyebrow("Settings · Local AI")
                        Text(
                            "On-device model",
                            style = MaterialTheme.typography.headlineMedium,
                            color = EnclaveTokens.Text,
                        )
                    }
                },
                navigationIcon = navigationIcon,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EnclaveTokens.Bg),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ActiveModelCard(configured, modelState)

            Eyebrow("Available models")
            EnclaveCard {
                ModelCatalogue.forEachIndexed { index, entry ->
                    if (index > 0) EnclaveDivider()
                    ModelRow(
                        entry = entry,
                        isConfigured = entry.reference == configured,
                        modelState = modelState,
                        onSelect = { viewModel.setModel(entry.reference) },
                        onDownload = {
                            viewModel.setModel(entry.reference)
                            viewModel.downloadModel()
                        },
                    )
                }
            }

            ModelReferenceField(
                configured = settings.llmModelUrl,
                onApply = viewModel::setModel,
            )

            Text(
                "Models are pulled anonymously from registry.ollama.ai with two plain GETs — a " +
                    "manifest, then the GGUF layer itself. Downloads resume if interrupted.",
                style = MaterialTheme.typography.labelSmall,
                color = EnclaveTokens.TextDim,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }
}

/** The accent-tinted card describing whichever model is currently configured. */
@Composable
private fun ActiveModelCard(reference: String, state: LlmModelState) {
    val entry = ModelCatalogue.firstOrNull { it.reference == reference }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(EnclaveTokens.RadiusCard))
            .background(
                Brush.linearGradient(
                    listOf(
                        EnclaveTokens.Accent.copy(alpha = 0.20f),
                        EnclaveTokens.AccentDeep.copy(alpha = 0.10f),
                    )
                )
            )
            .border(
                1.dp,
                EnclaveTokens.Accent.copy(alpha = 0.35f),
                RoundedCornerShape(EnclaveTokens.RadiusCard),
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                reference,
                style = MaterialTheme.typography.titleLarge,
                color = EnclaveTokens.Text,
            )
            StatusPill(state.shortLabel(), state.statusColor())
        }

        Text(
            entry?.subtitle ?: "Custom model",
            style = MaterialTheme.typography.labelMedium,
            color = EnclaveTokens.TextMuted,
        )

        // Download progress is the one number here that is genuinely live.
        if (state is LlmModelState.Downloading) {
            val progress = state.progress
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(EnclaveTokens.SurfaceVariant)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress ?: 0f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(EnclaveTokens.AccentGradient)
                    )
                }
                Text(
                    progress?.let { "Downloading… ${(it * 100).toInt()}%" } ?: "Downloading…",
                    style = MaterialTheme.typography.labelSmall,
                    color = EnclaveTokens.TextMuted,
                )
            }
        }

        if (state is LlmModelState.ErrorDownloading || state is LlmModelState.ErrorLoading) {
            Text(
                state.shortLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = EnclaveTokens.Danger,
            )
        }
    }
}

@Composable
private fun ModelRow(
    entry: ModelEntry,
    isConfigured: Boolean,
    modelState: LlmModelState,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
) {
    EnclaveSettingRow(
        title = entry.reference,
        description = entry.subtitle,
        onClick = if (isConfigured) null else onSelect,
        trailing = {
            when {
                isConfigured && modelState is LlmModelState.Ready ->
                    StatusPill("Active", EnclaveTokens.Ok)

                isConfigured && modelState is LlmModelState.Downloading ->
                    StatusPill(
                        modelState.progress?.let { "${(it * 100).toInt()}%" } ?: "…",
                        EnclaveTokens.Accent,
                    )

                isConfigured -> StatusPill("Selected", EnclaveTokens.Accent)

                else -> Text(
                    if (entry.isDefault) "Get" else "Download",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = EnclaveTokens.Accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(EnclaveTokens.SurfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                        .then(Modifier)
                )
            }
        },
    )
}

/**
 * The free-text model field. Edits are held locally and only committed on "Use this model", so
 * typing does not restart a download on every keystroke.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelReferenceField(configured: String, onApply: (String) -> Unit) {
    var input by rememberSaveable(configured) { mutableStateOf(configured) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Eyebrow("Ollama model or GGUF URL")
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = {
                Text(GgufModelManager.defaultModelUrl, color = EnclaveTokens.TextDim)
            },
            supportingText = {
                Text(
                    "e.g. tinydolphin, qwen2.5:0.5b, or an https:// link to a .gguf",
                    style = MaterialTheme.typography.labelSmall,
                    color = EnclaveTokens.TextDim,
                )
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
        if (input.trim() != configured) {
            EnclavePrimaryButton("Use this model", onClick = { onApply(input) })
        }
    }
}

/** One entry in the model catalogue shown on this screen. */
data class ModelEntry(
    val reference: String,
    val subtitle: String,
    val isDefault: Boolean = false,
)

/**
 * The three models the handoff lists. Sizes are the real byte counts of the GGUF layers, checked
 * against `registry.ollama.ai`.
 */
val ModelCatalogue = listOf(
    ModelEntry("qwen2.5:0.5b", "Default · 380 MB · ChatML · multilingual", isDefault = true),
    ModelEntry("tinydolphin", "1.1B · 610 MB · English-centric, fast"),
    ModelEntry("qwen2.5:1.5b", "Best quality · 1.0 GB · needs more RAM"),
)

private fun LlmModelState.shortLabel(): String = when (this) {
    LlmModelState.Disabled -> "Off"
    is LlmModelState.NotDownloaded -> "Not downloaded"
    is LlmModelState.Downloading -> "Downloading"
    is LlmModelState.ErrorDownloading -> "Download failed: ${throwable.message}"
    LlmModelState.NotLoaded -> "On disk"
    LlmModelState.Loading -> "Loading"
    LlmModelState.Ready -> "Loaded"
    is LlmModelState.ErrorLoading -> "Load failed: ${throwable.message}"
}

private fun LlmModelState.statusColor(): Color = when (this) {
    LlmModelState.Ready -> EnclaveTokens.Ok
    is LlmModelState.ErrorDownloading, is LlmModelState.ErrorLoading -> EnclaveTokens.Danger
    is LlmModelState.Downloading, LlmModelState.Loading -> EnclaveTokens.Accent
    else -> EnclaveTokens.TextDim
}

@Preview(showBackground = true, backgroundColor = 0xFF0F0A1E)
@Composable
private fun ActiveModelCardPreview() {
    AppTheme(theme = SettingsTheme.THEME_DARK) {
        Column(
            Modifier
                .background(EnclaveTokens.Bg)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ActiveModelCard("qwen2.5:0.5b", LlmModelState.Ready)
            ActiveModelCard("tinydolphin", LlmModelState.Downloading(0.42f))
        }
    }
}
