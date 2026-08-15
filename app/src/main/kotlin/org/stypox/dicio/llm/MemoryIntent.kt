package org.stypox.dicio.llm

/**
 * Decides whether the user actually **asked** to be remembered, rather than merely happening to
 * mention something about themselves.
 *
 * Small models take "save durable facts about the user" as an invitation to save almost every turn,
 * which fills the memory file with noise and — because saving is a tool call, and a tool call
 * becomes the answer — replaces the reply the user was waiting for. Gating on an explicit phrase
 * means the `remember` tool is not even offered to the model unless the user said something like
 * "remember that…", so it cannot be called by accident.
 *
 * This is deliberately a keyword check and not a classifier: it has to be predictable. A user who
 * says "remember" gets a memory; a user who says "I had a greek salad for lunch" does not.
 */
object MemoryIntent {

    /**
     * Phrases that count as an explicit request to remember, per language.
     *
     * Matching is substring-based on the lower-cased utterance, which suits the languages listed
     * here. Adding a language means adding its natural phrasings; there is no need to enumerate
     * every inflection, since the phrases are already the common spoken forms.
     */
    private val REQUEST_PHRASES = listOf(
        // English
        "remember that", "remember this", "remember i", "remember my", "remember me",
        "don't forget", "do not forget", "keep in mind", "make a note", "note that",
        "memorize", "memorise", "save that", "store that",
        // German
        "merk dir", "merke dir", "merk dir mal", "nicht vergessen", "denk daran",
        "behalte", "speichere", "notiere",
        // Italian
        "ricorda che", "ricordati", "non dimenticare", "annota",
        // Spanish
        "recuerda que", "recuérdame", "no olvides", "apunta",
        // French
        "souviens-toi", "rappelle-toi", "n'oublie pas", "retiens",
        // Dutch
        "onthoud dat", "onthoud", "niet vergeten",
        // Polish
        "zapamiętaj", "nie zapomnij",
        // Czech
        "zapamatuj si", "nezapomeň",
        // Swedish
        "kom ihåg", "glöm inte",
    )

    /**
     * Phrases asking the assistant to *forget*, which should equally not be mistaken for an
     * ordinary request. Recognised separately so the caller can route it to clearing rather than
     * saving.
     */
    private val FORGET_PHRASES = listOf(
        "forget that", "forget what", "forget my", "forget everything",
        "vergiss", "vergessen sie", "dimentica", "olvida", "oublie", "vergeet",
        "zapomnij", "zapomeň", "glöm bort",
    )

    /** Whether [utterance] explicitly asks the assistant to remember something. */
    fun isRememberRequest(utterance: String): Boolean {
        val text = utterance.lowercase().trim()
        if (text.isEmpty()) return false
        return REQUEST_PHRASES.any { text.contains(it) }
    }

    /** Whether [utterance] explicitly asks the assistant to forget something. */
    fun isForgetRequest(utterance: String): Boolean {
        val text = utterance.lowercase().trim()
        if (text.isEmpty()) return false
        return FORGET_PHRASES.any { text.contains(it) }
    }
}
