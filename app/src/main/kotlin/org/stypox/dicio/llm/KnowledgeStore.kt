package org.stypox.dicio.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/**
 * A fully offline, human-readable **long-term memory** for the assistant.
 *
 * Everything the model "learns" about the user is appended, as plain text bullet points, to a
 * Markdown file on the device. The same file is:
 *  - **read** back into every prompt (so the model has access to what it learned before), and
 *  - **written** to by the model via the `remember` tool
 *    ([org.stypox.dicio.llm.orchestrator.tools.RememberTool]), and
 *  - **viewable by the user** — [markdownFile] is exposed so a settings screen can render it, and
 *    it lives under the app's `files/` dir where it can also be exported.
 *
 * No network is involved at any point; the knowledge never leaves the device.
 *
 * The file is capped at [MAX_CHARS] characters (oldest facts drop off) to keep the prompt bounded.
 */
class KnowledgeStore(appContext: Context) {

    /** The Markdown file that holds everything the assistant has learned about the user. */
    val markdownFile: File = File(appContext.filesDir, FILE_NAME)

    private val mutex = Mutex()

    private val _content = MutableStateFlow("")

    /** The current Markdown content, observable so a settings screen can show it live. */
    val content: StateFlow<String> = _content

    init {
        // load whatever is already on disk (best-effort, synchronous, tiny file)
        _content.value = try {
            if (markdownFile.exists()) markdownFile.readText() else defaultHeader()
        } catch (e: Exception) {
            Log.w(TAG, "Could not read knowledge file", e)
            defaultHeader()
        }
    }

    /**
     * Returns the learned knowledge to inject into the system prompt, or an empty string if nothing
     * has been learned yet (so we don't waste prompt tokens on just a header).
     */
    fun promptContext(): String {
        val text = _content.value
        val facts = text.lineSequence().filter { it.trimStart().startsWith("- ") }.toList()
        if (facts.isEmpty()) return ""
        return buildString {
            append("What you already know about the user (from earlier conversations):\n")
            facts.forEach { append(it.trim()).append('\n') }
        }
    }

    /**
     * Appends a new fact learned about the user. De-duplicates against existing facts (case
     * insensitive) so the model repeating itself doesn't bloat the file. Returns true if it was a
     * new fact, false if it was already known.
     */
    suspend fun remember(fact: String): Boolean = mutex.withLock {
        val clean = fact.trim().removePrefix("-").trim()
        if (clean.isBlank()) return false

        val current = _content.value.ifBlank { defaultHeader() }
        val alreadyKnown = current.lineSequence()
            .any { it.trim().removePrefix("-").trim().equals(clean, ignoreCase = true) }
        if (alreadyKnown) return false

        val date = try {
            LocalDate.now().toString()
        } catch (e: Exception) {
            ""
        }
        val line = if (date.isEmpty()) "- $clean" else "- $clean _(learned $date)_"
        var updated = current.trimEnd() + "\n" + line + "\n"
        updated = capToLimit(updated)

        _content.value = updated
        withContext(Dispatchers.IO) {
            try {
                markdownFile.writeText(updated)
            } catch (e: Exception) {
                Log.e(TAG, "Could not write knowledge file", e)
            }
        }
        return true
    }

    /**
     * Replaces the whole file with [markdown], as edited by the user in settings.
     *
     * The text is capped the same way an appended fact is, so hand-editing cannot grow the prompt
     * without bound. Whatever the user writes is authoritative: no attempt is made to reformat it,
     * because [promptContext] only reads lines starting with "- " and ignores the rest, so free
     * prose in the file is harmless.
     */
    suspend fun replaceAll(markdown: String) = mutex.withLock {
        val capped = capToLimit(markdown)
        _content.value = capped
        withContext(Dispatchers.IO) {
            try {
                markdownFile.writeText(capped)
            } catch (e: Exception) {
                Log.e(TAG, "Could not write edited knowledge file", e)
            }
        }
    }

    /** Clears all learned knowledge (offered to the user in settings). */
    suspend fun clear() = mutex.withLock {
        _content.value = defaultHeader()
        withContext(Dispatchers.IO) {
            try {
                markdownFile.writeText(defaultHeader())
            } catch (e: Exception) {
                Log.e(TAG, "Could not clear knowledge file", e)
            }
        }
    }

    private fun capToLimit(text: String): String {
        if (text.length <= MAX_CHARS) return text
        // keep the header plus the most recent facts
        val lines = text.lines().toMutableList()
        val headerEnd = lines.indexOfFirst { it.trimStart().startsWith("- ") }
            .takeIf { it > 0 } ?: 0
        val header = lines.subList(0, headerEnd).toList()
        val facts = lines.subList(headerEnd, lines.size).toMutableList()
        var result = (header + facts).joinToString("\n")
        while (result.length > MAX_CHARS && facts.size > 1) {
            facts.removeAt(0) // drop oldest fact
            result = (header + facts).joinToString("\n")
        }
        return result
    }

    private fun defaultHeader(): String = """
        # What Enclave has learned about you

        This file is your assistant's offline memory. Everything here stays on your device.
        Enclave reads it before answering, and only adds to it when you explicitly ask it to
        remember something. Lines starting with "- " are the facts it uses; anything else here is
        just notes for you. Edit or clear it any time.

    """.trimIndent() + "\n"

    companion object {
        private val TAG = KnowledgeStore::class.simpleName
        private const val FILE_NAME = "assistant-memory.md"
        private const val MAX_CHARS = 8_000
    }
}
