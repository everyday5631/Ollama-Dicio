package org.stypox.dicio.llm.orchestrator.tools

import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillOutput
import org.stypox.dicio.llm.KnowledgeStore
import org.stypox.dicio.llm.LlmToolDef
import org.stypox.dicio.llm.LlmToolParam
import org.stypox.dicio.llm.orchestrator.LlmAnswerOutput
import org.stypox.dicio.llm.orchestrator.LlmTool

/**
 * Lets the model persist something it learned about the user into the offline [KnowledgeStore]
 * (the `assistant-memory.md` Markdown file). This is the "mitlernen" capability: the model is
 * instructed to call this whenever the user shares a durable preference or fact about themselves.
 *
 * Everything stays on device; the file is also readable by the user in settings.
 */
class RememberTool(
    private val knowledgeStore: KnowledgeStore,
) : LlmTool {
    override val definition = LlmToolDef(
        name = NAME,
        description = "Save something the user has just explicitly asked you to remember. " +
            "Only call this when the user actually asked -- e.g. \"remember that...\", " +
            "\"don't forget...\". Never call it for something they merely mentioned.",
        params = listOf(
            LlmToolParam(
                name = "fact",
                type = "string",
                description = "The fact to remember, phrased as a short standalone statement, " +
                    "e.g. 'The user's name is Eddie' or 'The user prefers metric units'.",
            )
        ),
    )

    override suspend fun execute(ctx: SkillContext, args: Map<String, String>): SkillOutput {
        val fact = args["fact"]?.trim().orEmpty()
        if (fact.isBlank()) {
            return LlmAnswerOutput("I didn't catch what I should remember.")
        }
        val isNew = knowledgeStore.remember(fact)
        // Echo the fact back. A bare "Okay" leaves the user unsure what was stored, and an empty
        // string leaves them with no answer at all -- this tool call *is* the turn's reply, so it
        // must always say something.
        return LlmAnswerOutput(
            if (isNew) "Got it — I'll remember: $fact" else "I already knew that: $fact"
        )
    }

    companion object {
        /** The tool's name, so the orchestrator can hide it without repeating the string. */
        const val NAME = "remember"
    }
}
