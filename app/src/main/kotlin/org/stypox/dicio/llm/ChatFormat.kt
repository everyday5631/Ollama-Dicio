package org.stypox.dicio.llm

/**
 * Builds the raw prompt string fed to the model from a list of [LlmMessage]s and the available
 * [LlmToolDef]s.
 *
 * Small on-device models are trained on one specific turn format and degrade noticeably when given
 * another, so [build] takes a [PromptStyle] and renders accordingly. See [PromptStyle] for which
 * families map to which.
 *
 * The tool list is injected into the system message together with the calling convention described
 * in `docs/local-llm.md`.
 */
object ChatFormat {
    private const val IM_START = "<|im_start|>"
    private const val IM_END = "<|im_end|>"

    /**
     * The instruction block that teaches the model how to call tools. Kept deliberately short and
     * explicit because small models follow terse instructions better.
     */
    fun toolInstructions(tools: List<LlmToolDef>): String {
        if (tools.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("You can call tools to act on the user's device. ")
        sb.append("To call a tool, reply with ONLY one line of JSON and nothing else:\n")
        sb.append("{\"tool\": \"<name>\", \"arguments\": {<args>}}\n")
        sb.append("If no tool is needed, just answer in plain language. Available tools:\n")
        for (tool in tools) {
            sb.append("- ").append(tool.name).append(": ").append(tool.description)
            if (tool.params.isNotEmpty()) {
                sb.append(" Arguments: ")
                sb.append(tool.params.joinToString(", ") { p ->
                    "${p.name} (${p.type}${if (p.required) "" else ", optional"}): ${p.description}"
                })
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    /**
     * Renders [messages] into a single prompt string, appending the tool instructions to the
     * (first) system message, and ending with an open assistant turn so the model continues.
     */
    fun build(
        messages: List<LlmMessage>,
        tools: List<LlmToolDef>,
        style: PromptStyle = PromptStyle.CHAT_ML,
    ): String = when (style) {
        PromptStyle.CHAT_ML -> buildChatMl(messages, tools)
        PromptStyle.GEMMA -> buildGemma(messages, tools)
    }

    private fun buildChatMl(messages: List<LlmMessage>, tools: List<LlmToolDef>): String {
        val sb = StringBuilder()
        val toolBlock = toolInstructions(tools)

        var injectedTools = toolBlock.isEmpty()
        for (message in messages) {
            val content = when (message.role) {
                LlmRole.SYSTEM -> if (!injectedTools) {
                    injectedTools = true
                    message.content + "\n\n" + toolBlock
                } else {
                    message.content
                }
                LlmRole.TOOL -> "Result of ${message.toolName ?: "tool"}: ${message.content}"
                else -> message.content
            }
            sb.append(IM_START).append(message.role.wire).append('\n')
                .append(content).append(IM_END).append('\n')
        }

        // if there was no system message to attach the tool block to, prepend one
        if (!injectedTools) {
            val sysBlock = IM_START + LlmRole.SYSTEM.wire + "\n" + toolBlock + IM_END + "\n"
            sb.insert(0, sysBlock)
        }

        sb.append(IM_START).append(LlmRole.ASSISTANT.wire).append('\n')
        return sb.toString()
    }

    /**
     * Renders the Gemma turn format.
     *
     * Gemma has no system role, so the system text (persona, tool instructions, memory) is
     * prepended to the **first user turn**, which is exactly what Gemma's own template does. Tool
     * results are folded into a user turn too, since `user` and `model` are the only roles the
     * format has.
     */
    private fun buildGemma(messages: List<LlmMessage>, tools: List<LlmToolDef>): String {
        val toolBlock = toolInstructions(tools)
        val systemText = buildString {
            messages.filter { it.role == LlmRole.SYSTEM }
                .forEach { append(it.content).append('\n') }
            if (toolBlock.isNotEmpty()) {
                append('\n').append(toolBlock)
            }
        }.trim()

        val sb = StringBuilder()
        var systemPending = systemText.isNotEmpty()

        for (message in messages) {
            when (message.role) {
                LlmRole.SYSTEM -> continue // already folded into systemText

                LlmRole.ASSISTANT -> sb
                    .append(GEMMA_START).append("model\n")
                    .append(message.content).append(GEMMA_END).append('\n')

                LlmRole.USER, LlmRole.TOOL -> {
                    val body = if (message.role == LlmRole.TOOL) {
                        "Result of ${message.toolName ?: "tool"}: ${message.content}"
                    } else {
                        message.content
                    }
                    sb.append(GEMMA_START).append("user\n")
                    if (systemPending) {
                        systemPending = false
                        sb.append(systemText).append("\n\n")
                    }
                    sb.append(body).append(GEMMA_END).append('\n')
                }
            }
        }

        // if there was no user turn to attach the system text to, emit it as one
        if (systemPending) {
            sb.append(GEMMA_START).append("user\n").append(systemText).append(GEMMA_END).append('\n')
        }

        sb.append(GEMMA_START).append("model\n")
        return sb.toString()
    }

    private const val GEMMA_START = "<start_of_turn>"
    private const val GEMMA_END = "<end_of_turn>"
}
