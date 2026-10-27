package com.takeruf.nagi.browser.translation

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

/** Text stays on device. Downloads contain language models, never page content. */
interface TextTranslationBackend : AutoCloseable {
    suspend fun identify(text: String): String?
    suspend fun prepare(source: String, target: String, wifiOnly: Boolean)
    suspend fun translate(text: String): String
}

class MlKitTextTranslationBackend : TextTranslationBackend {
    private val identifierDelegate = lazy { LanguageIdentification.getClient() }
    private val identifier by identifierDelegate
    private var translator: Translator? = null

    override suspend fun identify(text: String): String? =
        TranslateLanguage.fromLanguageTag(identifier.identifyLanguage(text.take(4000)).await())

    override suspend fun prepare(source: String, target: String, wifiOnly: Boolean) {
        translator?.close()
        val client = Translation.getClient(TranslatorOptions.Builder()
            .setSourceLanguage(source).setTargetLanguage(target).build())
        translator = client
        val conditions = DownloadConditions.Builder().apply { if (wifiOnly) requireWifi() }.build()
        client.downloadModelIfNeeded(conditions).await()
    }

    override suspend fun translate(text: String): String = checkNotNull(translator).translate(text).await()
    override fun close() {
        translator?.close(); translator = null
        if (identifierDelegate.isInitialized()) identifier.close()
    }
}
