package org.stypox.dicio.llm.orchestrator

import org.stypox.dicio.llm.LlmToolDef

/**
 * Holds the set of [LlmTool]s available to the orchestrator and looks them up by name.
 */
class ToolRegistry(tools: List<LlmTool>) {

    private val byName: Map<String, LlmTool> = tools.associateBy { it.definition.name }

    /** All tool definitions, to render into the prompt. */
    val definitions: List<LlmToolDef> = tools.map { it.definition }

    /**
     * The tool definitions for one turn, with [exclude]d names left out.
     *
     * A tool the model cannot see is a tool it cannot call, which is a far stronger guarantee than
     * instructing it not to. Used to hide `remember` unless the user actually asked for it.
     */
    fun definitionsExcluding(exclude: Set<String>): List<LlmToolDef> =
        if (exclude.isEmpty()) definitions else definitions.filter { it.name !in exclude }

    /** Returns the tool with the given [name], or null if there is no such tool. */
    fun find(name: String): LlmTool? = byName[name]

    val isEmpty: Boolean get() = byName.isEmpty()
}
