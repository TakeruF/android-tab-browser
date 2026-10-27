package com.takeruf.nagi.browser.translation

enum class TranslationStage { IDLE, DETECTING, DOWNLOADING, TRANSLATING, TRANSLATED, ERROR }
enum class TranslationError { NO_TEXT, LANGUAGE, SAME_LANGUAGE, MODEL, TRANSLATION, LIMIT }

data class PageTranslationState(
    val stage: TranslationStage = TranslationStage.IDLE,
    val source: String? = null,
    val target: String? = null,
    val translatedNodes: Int = 0,
    val error: TranslationError? = null,
) {
    val active get() = stage != TranslationStage.IDLE
    val busy get() = stage == TranslationStage.DETECTING || stage == TranslationStage.DOWNLOADING || stage == TranslationStage.TRANSLATING
}
