package org.stypox.dicio.settings

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.datastore.core.DataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.stypox.dicio.health.HealthDataStore
import org.stypox.dicio.health.HealthImportManager
import org.stypox.dicio.llm.GgufModelManager
import org.stypox.dicio.llm.KnowledgeStore
import org.stypox.dicio.llm.LlmService
import org.stypox.dicio.settings.datastore.UserSettings
import org.stypox.dicio.util.toStateFlowDistinctBlockingFirst
import javax.inject.Inject

/**
 * Backs the "Local AI" settings screen: the enable/learning toggles, the model download state, the
 * offline memory Markdown, and the fitness/health import (Gadgetbridge ZIP / GPX).
 */
@HiltViewModel
class LocalAiViewModel @Inject constructor(
    application: Application,
    private val dataStore: DataStore<UserSettings>,
    private val modelManager: GgufModelManager,
    val knowledgeStore: KnowledgeStore,
    val healthDataStore: HealthDataStore,
    private val healthImportManager: HealthImportManager,
) : AndroidViewModel(application) {

    val settingsState = dataStore.data.toStateFlowDistinctBlockingFirst(viewModelScope)

    val modelState = modelManager.state
    val importState = healthImportManager.state
    val memoryContent = knowledgeStore.content
    val healthData = healthDataStore.data

    private fun currentModelUrl(): String =
        settingsState.value.llmModelUrl.ifBlank { GgufModelManager.defaultModelUrl }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.updateData { it.toBuilder().setLlmEnabled(enabled).build() }
            val app = getApplication<Application>()
            if (enabled) {
                modelManager.refresh(enabled = true, modelUrl = currentModelUrl())
                LlmService.start(app)
            } else {
                LlmService.stop(app)
            }
        }
    }

    /**
     * Sets the model to use: an Ollama reference such as `tinydolphin` or `qwen2.5:0.5b`, or a
     * plain `https://` URL of a GGUF file. Blank resets to [GgufModelManager.defaultModelUrl].
     * Changing this makes the manager notice the model changed, so the next download fetches the
     * new one instead of reusing the file already on disk.
     */
    fun setModel(model: String) {
        viewModelScope.launch {
            dataStore.updateData { it.toBuilder().setLlmModelUrl(model.trim()).build() }
            modelManager.refresh(
                enabled = settingsState.value.llmEnabled,
                modelUrl = currentModelUrl(),
            )
        }
    }

    fun setLearningEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.updateData { it.toBuilder().setLlmLearningEnabled(enabled).build() }
        }
    }

    /** Starts (or resumes) downloading + loading the model. */
    fun downloadModel() {
        modelManager.refresh(enabled = true, modelUrl = currentModelUrl())
        modelManager.ensureReady(currentModelUrl())
    }

    fun clearMemory() {
        viewModelScope.launch { knowledgeStore.clear() }
    }

    /** Saves the memory file as the user edited it in settings. */
    fun saveMemory(markdown: String) {
        viewModelScope.launch { knowledgeStore.replaceAll(markdown) }
    }

    /** Imports fitness/health data from a picked file (ZIP export or GPX). */
    fun importHealth(uri: Uri) {
        viewModelScope.launch {
            val name = queryDisplayName(uri) ?: uri.lastPathSegment ?: "import"
            healthImportManager.import(uri, name)
        }
    }

    fun clearHealth() {
        viewModelScope.launch { healthDataStore.clear() }
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        getApplication<Application>().contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
    } catch (e: Exception) {
        null
    }
}
