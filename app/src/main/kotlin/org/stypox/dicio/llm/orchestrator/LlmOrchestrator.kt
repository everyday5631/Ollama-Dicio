package org.stypox.dicio.llm.orchestrator

import android.util.Log
import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.first
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillOutput
import org.stypox.dicio.io.graphical.ErrorSkillOutput
import org.stypox.dicio.llm.GgufModelManager
import org.stypox.dicio.llm.KnowledgeStore
import org.stypox.dicio.llm.LlmEngine
import org.stypox.dicio.llm.LlmEvent
import org.stypox.dicio.llm.LlmMessage
import org.stypox.dicio.llm.LlmModelState
import org.stypox.dicio.llm.MemoryIntent
import org.stypox.dicio.llm.orchestrator.tools.RememberTool
import org.stypox.dicio.llm.LlmRole

/**
 * The brain of the "model decides" routing: every user turn is sent to the on-device model, which
 * either answers directly or calls one of the registered [LlmTool]s (which are backed by the
 * existing Dicio skills). See `docs/local-llm.md` for the overall flow.
 *
 * It also drives the offline "mitlernen" feature: the learned-knowledge Markdown ([KnowledgeStore])
 * is injected into the system prompt, and the model is told to call the `remember` tool when the
 * user shares durable facts about themselves.
 *
 * Generation is currently awaited to completion and returned as a single [SkillOutput] (Dicio's
 * skill layer is not streaming). Token streaming to the UI could be layered on later without
 * changing the tools or the engine.
 */
class LlmOrchestrator(
    private val engine: LlmEngine,
    private val modelManager: GgufModelManager,
    private val toolRegistry: ToolRegistry,
    private val knowledgeStore: KnowledgeStore,
    private val healthDataStore: org.stypox.dicio.health.HealthDataStore,
    private val dataStore: DataStore<org.stypox.dicio.settings.datastore.UserSettings>,
) {

    /**
     * Handles a user turn. If the model is not ready yet, returns a friendly status message instead
     * of blocking. Otherwise routes through the model and returns the resulting output.
     *
     * @param ctx the skill context
     * @param userInput the recognized user utterance
     * @param history prior turns in this conversation (oldest first), for multi-turn context
     */
    suspend fun handle(
        ctx: SkillContext,
        userInput: String,
        history: List<LlmMessage> = emptyList(),
    ): SkillOutput {
        // make sure the model is loaded (idempotent, coalesced)
        modelManager.ensureReady(modelManager.modelPath)
        when (val s = modelManager.state.value) {
            is LlmModelState.Downloading ->
                return LlmAnswerOutput("The local AI model is still downloading, please try again in a moment.")
            LlmModelState.Loading, LlmModelState.NotLoaded ->
                return LlmAnswerOutput("The local AI model is loading, please try again in a moment.")
            is LlmModelState.NotDownloaded ->
                return LlmAnswerOutput("The local AI model still needs to be downloaded. Open settings to start the download.")
            is LlmModelState.ErrorDownloading ->
                return ErrorSkillOutput(s.throwable, false)
            is LlmModelState.ErrorLoading ->
                return ErrorSkillOutput(s.throwable, false)
            LlmModelState.Disabled ->
                return LlmAnswerOutput("The local AI is turned off. Enable it in settings.")
            LlmModelState.Ready -> { /* proceed */ }
        }

        val learningEnabled = dataStore.data.first().llmLearningEnabled
        // The `remember` tool is offered only when the user actually asked to be remembered.
        // Left always-on, a small model calls it on most turns -- and because a tool call becomes
        // the turn's reply, that both fills the memory file with noise and swallows the answer.
        val rememberRequested = learningEnabled && MemoryIntent.isRememberRequest(userInput)
        val messages = buildMessages(userInput, history, learningEnabled, rememberRequested)
        val tools = if (rememberRequested) {
            toolRegistry.definitions
        } else {
            toolRegistry.definitionsExcluding(setOf(RememberTool.NAME))
        }

        return try {
            var toolCall: org.stypox.dicio.llm.LlmToolCall? = null
            var answer = ""
            var error: Throwable? = null

            engine.generate(messages, tools).collect { event ->
                when (event) {
                    is LlmEvent.Token -> { /* accumulated below via Done */ }
                    is LlmEvent.ToolCall -> toolCall = event.call
                    is LlmEvent.Done -> answer = event.fullText
                    is LlmEvent.Error -> error = event.throwable
                }
            }

            when {
                error != null -> ErrorSkillOutput(error!!, false)
                toolCall != null -> executeTool(ctx, toolCall!!, answer)
                else -> LlmAnswerOutput(answer.ifBlank {
                    "Sorry, I couldn't come up with an answer."
                })
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Orchestration failed", t)
            ErrorSkillOutput(t, false)
        }
    }

    private suspend fun executeTool(
        ctx: SkillContext,
        call: org.stypox.dicio.llm.LlmToolCall,
        fallbackAnswer: String,
    ): SkillOutput {
        val tool = toolRegistry.find(call.name)
        if (tool == null) {
            Log.w(TAG, "Model requested unknown tool '${call.name}'")
            // model hallucinated a tool: fall back to any text it produced
            return LlmAnswerOutput(
                fallbackAnswer.ifBlank { "I can't do that yet." }
            )
        }
        return tool.execute(ctx, call.arguments)
    }

    private fun buildMessages(
        userInput: String,
        history: List<LlmMessage>,
        learningEnabled: Boolean,
        rememberRequested: Boolean,
    ): List<LlmMessage> {
        val sb = StringBuilder()
        sb.append(SYSTEM_PERSONA)
        if (learningEnabled) {
            // Only ask for a save on the turn the user requested one. Otherwise the memory is
            // read-only context: the model may use what it knows, but must not add to it.
            sb.append("\n\n").append(
                if (rememberRequested) SAVE_INSTRUCTION else RECALL_INSTRUCTION
            )
            val knowledge = knowledgeStore.promptContext()
            if (knowledge.isNotBlank()) {
                sb.append("\n\n").append(knowledge)
            }
        }

        // Always make imported fitness/health data available as context (independent of the
        // learning toggle), so the model can answer questions about the user's activity offline.
        val health = healthDataStore.promptContext()
        if (health.isNotBlank()) {
            sb.append("\n\n").append(health)
        }

        val messages = ArrayList<LlmMessage>(history.size + 2)
        messages.add(LlmMessage(LlmRole.SYSTEM, sb.toString()))
        messages.addAll(history)
        messages.add(LlmMessage(LlmRole.USER, userInput))
        return messages
    }

    companion object {
        private val TAG = LlmOrchestrator::class.simpleName

        private const val SYSTEM_PERSONA =
            "You are Enclave, a helpful offline voice assistant running on the user's phone. " +
                "Answer concisely and in the same language the user speaks. When a tool can " +
                "fulfil the request, call it instead of guessing."

        /** Used on ordinary turns: the memory is context to read, never to write. */
        private const val RECALL_INSTRUCTION =
            "Below is what you already know about the user. Use it to personalise your answers " +
                "when it is relevant. Do not mention the list itself unless asked, and do not " +
                "try to add anything to it."

        /** Used only when the user explicitly asked to be remembered. */
        private const val SAVE_INSTRUCTION =
            "The user has just asked you to remember something. Call the 'remember' tool once, " +
                "with the fact phrased as a short standalone statement. Below is what you " +
                "already know about them."
    }
}
