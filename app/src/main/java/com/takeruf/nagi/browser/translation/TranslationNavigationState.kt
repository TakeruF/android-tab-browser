package com.takeruf.nagi.browser.translation

data class TranslationChoice(val target: String, val wifiOnly: Boolean = true)
data class TranslatedLanguagePair(val source: String, val choice: TranslationChoice)

/** One instance per tab. A new document must identify its language before reusing this choice. */
class TranslationNavigationState {
    private var translated: TranslatedLanguagePair? = null
    private var pending: TranslatedLanguagePair? = null

    fun didTranslate(source: String, target: String, wifiOnly: Boolean) {
        translated = TranslatedLanguagePair(source, TranslationChoice(target, wifiOnly))
    }
    fun followLink() { pending = translated }
    fun documentStarted() { translated = null }
    fun reset() { translated = null; pending = null }
    fun takeContinuation(): TranslatedLanguagePair? = pending.also { pending = null }

    companion object {
        fun choose(source: String, continuation: TranslatedLanguagePair?, always: Map<String, String>): TranslationChoice? {
            val choice = continuation?.takeIf { it.source == source }?.choice
                ?: always[source]?.let { TranslationChoice(it) }
            return choice?.takeUnless { it.target == source }
        }
    }
}
