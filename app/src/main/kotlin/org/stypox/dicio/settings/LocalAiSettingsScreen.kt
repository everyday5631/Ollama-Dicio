package org.stypox.dicio.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.stypox.dicio.health.HealthImportManager
import org.stypox.dicio.llm.GgufModelManager
import org.stypox.dicio.llm.LlmModelState

/**
 * "Local AI" settings screen: enable the on-device model, download it, toggle offline learning,
 * inspect what the assistant has learned, and import fitness/health data (Gadgetbridge ZIP / GPX).
 *
 * Kept intentionally simple (plain Material3) so it is easy to adapt to the app's settings design.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalAiSettingsScreen(
    navigationIcon: @Composable () -> Unit,
    viewModel: LocalAiViewModel = hiltViewModel(),
) {
    val settings by viewModel.settingsState.collectAsState()
    val modelState by viewModel.modelState.collectAsState()
    val importState by viewModel.importState.collectAsState()
    val memory by viewModel.memoryContent.collectAsState()
    val health by viewModel.healthData.collectAsState()

    // file picker for Gadgetbridge ZIP or GPX
    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) viewModel.importHealth(uri) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Local AI") }, navigationIcon = navigationIcon)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // --- enable / learning toggles ---
            SwitchRow(
                title = "Use local AI (model decides)",
                subtitle = "Route every request to the on-device model; it answers or calls a skill.",
                checked = settings.llmEnabled,
                onCheckedChange = viewModel::setEnabled,
            )
            SwitchRow(
                title = "Learn about me offline",
                subtitle = "Save durable facts you share into an on-device Markdown file.",
                checked = settings.llmLearningEnabled,
                onCheckedChange = viewModel::setLearningEnabled,
            )

            // --- model status ---
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Model", style = MaterialTheme.typography.titleMedium)

                    // an Ollama reference, or a direct GGUF URL; kept in local state while being
                    // edited so typing does not restart the download on every keystroke
                    var modelInput by rememberSaveable(settings.llmModelUrl) {
                        mutableStateOf(settings.llmModelUrl)
                    }
                    OutlinedTextField(
                        value = modelInput,
                        onValueChange = { modelInput = it },
                        label = { Text("Ollama model or GGUF URL") },
                        placeholder = { Text(GgufModelManager.defaultModelUrl) },
                        supportingText = {
                            Text("e.g. tinydolphin, qwen2.5:0.5b, or an https:// link to a .gguf")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (modelInput.trim() != settings.llmModelUrl) {
                        OutlinedButton(onClick = { viewModel.setModel(modelInput) }) {
                            Text("Use this model")
                        }
                    }

                    Text(modelStateText(modelState), style = MaterialTheme.typography.bodyMedium)
                    (modelState as? LlmModelState.Downloading)?.progress?.let { p ->
                        LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                    }
                    if (modelState is LlmModelState.NotDownloaded ||
                        modelState is LlmModelState.NotLoaded ||
                        modelState is LlmModelState.ErrorDownloading ||
                        modelState is LlmModelState.ErrorLoading
                    ) {
                        Button(onClick = viewModel::downloadModel) {
                            Text("Download / load model")
                        }
                    }
                }
            }

            // --- fitness/health import ---
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Fitness & health data", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Import a Gadgetbridge ZIP export or a GPX file. Everything stays offline.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Imported: ${health.dailyMetrics.size} day(s), " +
                            "${health.activities.size} activity(ies).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    (importState as? HealthImportManager.ImportState.Error)?.let {
                        Text(it.message, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (importState is HealthImportManager.ImportState.Importing) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { pickFile.launch("*/*") }) { Text("Import ZIP / GPX") }
                        if (!health.isEmpty) {
                            OutlinedButton(onClick = viewModel::clearHealth) { Text("Clear") }
                        }
                    }
                }
            }

            // --- learned memory: viewer and editor ---
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("What the assistant has learned",
                        style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Enclave only adds to this when you ask it to remember something. " +
                            "Lines starting with \"- \" are the facts it uses.",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    // Editing is held locally and committed on Save, so a keystroke does not
                    // rewrite the file (and does not fight the flow that feeds `memory` back in).
                    var draft by rememberSaveable(memory) { mutableStateOf(memory) }
                    var editing by rememberSaveable { mutableStateOf(false) }

                    if (editing) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            label = { Text("assistant-memory.md") },
                            minLines = 6,
                            maxLines = 16,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                viewModel.saveMemory(draft)
                                editing = false
                            }) { Text("Save") }
                            OutlinedButton(onClick = {
                                draft = memory
                                editing = false
                            }) { Text("Cancel") }
                        }
                    } else {
                        Text(
                            memory.ifBlank { "Nothing learned yet." },
                            style = MaterialTheme.typography.bodySmall,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { draft = memory; editing = true }) {
                                Text("Edit")
                            }
                            OutlinedButton(onClick = viewModel::clearMemory) {
                                Text("Clear memory")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun modelStateText(state: LlmModelState): String = when (state) {
    LlmModelState.Disabled -> "Disabled."
    is LlmModelState.NotDownloaded -> "Not downloaded yet."
    is LlmModelState.Downloading -> state.progress?.let {
        "Downloading… ${(it * 100).toInt()}%"
    } ?: "Downloading…"
    is LlmModelState.ErrorDownloading -> "Download failed: ${state.throwable.message}"
    LlmModelState.NotLoaded -> "Downloaded, not loaded."
    LlmModelState.Loading -> "Loading into memory…"
    LlmModelState.Ready -> "Ready."
    is LlmModelState.ErrorLoading -> "Load failed: ${state.throwable.message}"
}
