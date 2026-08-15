package org.stypox.dicio.llm

/**
 * The prompt shape a model expects.
 *
 * Small instruct models are trained on one specific turn format and degrade noticeably when given
 * another, so the format has to follow the model rather than being fixed app-wide.
 *
 * The style is inferred from the configured model reference rather than from the GGUF's embedded
 * template: llama.cpp can apply that template itself, but the tool-calling convention has to be
 * injected into the system turn, and doing that reliably means owning the rendering.
 */
enum class PromptStyle {
    /**
     * ChatML — `<|im_start|>role … <|im_end|>`, with a real system turn.
     * Used by Qwen2.5 and the TinyLlama/TinyDolphin family.
     */
    CHAT_ML,

    /**
     * Gemma — `<start_of_turn>user … <end_of_turn>` / `<start_of_turn>model`.
     *
     * Gemma has **no system role**: its own template folds the system prompt into the first user
     * turn, and emitting `<start_of_turn>system` produces a turn the model never saw in training.
     * The renderer therefore prepends the system text to the first user message instead.
     */
    GEMMA,
    ;

    companion object {
        /**
         * Picks the style for a model reference such as `gemma3:270m`, `qwen2.5:0.5b`, or an
         * `https://…/something.gguf` URL.
         *
         * Matching is a substring check on the lower-cased reference, which is enough to tell the
         * families apart in both the Ollama and the file-name form (`gemma-3-270m-it-Q4_K_M.gguf`).
         * Anything unrecognised falls back to [CHAT_ML], the more common convention.
         */
        fun forModel(reference: String): PromptStyle {
            val ref = reference.lowercase()
            return when {
                "gemma" in ref -> GEMMA
                else -> CHAT_ML
            }
        }
    }
}
