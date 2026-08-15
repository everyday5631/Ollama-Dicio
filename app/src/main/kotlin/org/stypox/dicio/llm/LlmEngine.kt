package org.stypox.dicio.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The lifecycle state of an [LlmEngine].
 */
sealed interface LlmEngineState {
    /** No model loaded (initial state, or after [LlmEngine.unload]). */
    data object Unloaded : LlmEngineState

    /** A model is being loaded into memory. */
    data object Loading : LlmEngineState

    /** A model is loaded and ready to [LlmEngine.generate]. */
    data object Loaded : LlmEngineState

    /** Loading or a previous operation failed. */
    data class Error(val throwable: Throwable) : LlmEngineState
}

/**
 * A backend-agnostic on-device text generation engine.
 *
 * The default implementation is [LlamaCppEngine] (native llama.cpp), but this interface exists so
 * that the backend can be swapped (e.g. for an HTTP client to a local Ollama server, or a
 * MediaPipe LLM) without touching the orchestrator or the skill layer.
 *
 * Implementations must be safe to call [generate] on sequentially; concurrent generations are not
 * required to be supported (and [LlamaCppEngine] serializes them onto a single thread).
 */
interface LlmEngine {

    /**
     * The turn format the currently configured model expects; see [PromptStyle].
     *
     * Settable because the model can be swapped at runtime, and the format has to follow it.
     */
    var promptStyle: PromptStyle
        get() = PromptStyle.CHAT_ML
        set(_) { /* engines that render their own prompt can ignore this */ }

    val state: StateFlow<LlmEngineState>

    /**
     * Loads the model at [modelPath] into memory if it is not already loaded. Suspends until the
     * model is ready (or throws on failure). Safe to call repeatedly; a no-op if the same model is
     * already loaded.
     *
     * @param modelPath absolute path to a GGUF model file on disk
     */
    suspend fun ensureLoaded(modelPath: String)

    /**
     * Frees the model from memory. Safe to call when nothing is loaded.
     */
    fun unload()

    /**
     * Generates a response to the given [messages]. The [tools] are made available to the model
     * (rendered into the prompt); if the model decides to call one, a [LlmEvent.ToolCall] is
     * emitted instead of [LlmEvent.Done].
     *
     * The returned [Flow] emits [LlmEvent.Token]s as text streams in, followed by exactly one
     * terminal event ([LlmEvent.Done], [LlmEvent.ToolCall] or [LlmEvent.Error]). Cancelling the
     * flow's collection stops generation.
     */
    fun generate(messages: List<LlmMessage>, tools: List<LlmToolDef>): Flow<LlmEvent>
}
