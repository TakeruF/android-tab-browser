package com.takeruf.nagi.browser.translation

import android.webkit.WebView
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.coroutines.resume

/** Main-thread, engine-owned session. No JavaScript interface or remote page script is exposed. */
class PageTranslation(
    private val view: WebView,
    private val scope: CoroutineScope,
    private val factory: () -> TextTranslationBackend,
    private val onState: (PageTranslationState) -> Unit,
) {
    private val script by lazy { view.context.assets.open("browser/page-translation.js").bufferedReader().use { it.readText() } }
    private var job: Job? = null
    private var token: String? = null
    private var visible = true
    private var current = PageTranslationState()
    val active get() = current.active || job?.isActive == true

    fun setVisible(value: Boolean) { visible = value }
    private fun publish(value: PageTranslationState) { current = value; onState(value) }

    fun start(source: String?, target: String, wifiOnly: Boolean, onSource: (String) -> Unit = {}) {
        startSession(source, TranslationChoice(target, wifiOnly), null, onSource)
    }

    fun startAutomatic(choose: (String) -> TranslationChoice?) {
        startSession(null, null, choose, {})
    }

    private fun startSession(source: String?, requested: TranslationChoice?, automatic: ((String) -> TranslationChoice?)?,
        onSource: (String) -> Unit) {
        reset(restore = true)
        if ((requested != null && TranslateLanguage.fromLanguageTag(requested.target) != requested.target) ||
            (source != null && TranslateLanguage.fromLanguageTag(source) != source)) return
        val session = UUID.randomUUID().toString()
        token = session
        if (automatic == null) publish(PageTranslationState(TranslationStage.DETECTING, source, requested?.target))
        job = scope.launch {
            var backend: TextTranslationBackend? = null
            try {
                evaluate("($script)(${JSONObject.quote(session)}, ${JSONObject.quote(requested?.target.orEmpty())}); true")
                val sample = call(session, "sample()")?.takeIf { it.isNotBlank() }
                if (sample == null) {
                    if (automatic != null) reset(restore = true) else fail(TranslationError.NO_TEXT)
                    return@launch
                }
                val client = factory().also { backend = it }
                val language = source ?: withTimeout(15_000) { client.identify(sample) }
                if (language == null) {
                    if (automatic != null) reset(restore = true) else fail(TranslationError.LANGUAGE)
                    return@launch
                }
                val choice = requested ?: automatic?.invoke(language)
                if (choice == null || TranslateLanguage.fromLanguageTag(choice.target) != choice.target) {
                    reset(restore = true); return@launch
                }
                if (language == choice.target) {
                    if (automatic != null) reset(restore = true) else fail(TranslationError.SAME_LANGUAGE)
                    return@launch
                }
                call(session, "setTarget(${JSONObject.quote(choice.target)})")
                onSource(language)
                publish(current.copy(stage = TranslationStage.DOWNLOADING, source = language, target = choice.target))
                withTimeout(180_000) { client.prepare(language, choice.target, choice.wifiOnly) }
                val cache = object : LinkedHashMap<String, String>(256, 0.75f, true) {
                    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 256
                }
                while (isActive && token == session) {
                    if (!visible) { delay(600); continue }
                    val batch = JSONObject(checkNotNull(call(session, "take()")) { "Translation document is unavailable" })
                    val items = batch.getJSONArray("items")
                    if (items.length() == 0) {
                        publish(current.copy(stage = TranslationStage.TRANSLATED,
                            error = if (batch.optBoolean("limited")) TranslationError.LIMIT else null))
                        delay(600)
                        continue
                    }
                    publish(current.copy(stage = TranslationStage.TRANSLATING))
                    val results = JSONArray()
                    for (i in 0 until items.length()) {
                        ensureActive()
                        val item = items.getJSONObject(i)
                        val original = item.getString("text")
                        val translated = cache[original] ?: translateText(client, original).also { cache[original] = it }
                        results.put(JSONObject().put("id", item.getInt("id")).put("original", original).put("text", translated))
                    }
                    val count = call(session, "apply($results)")?.toIntOrNull() ?: 0
                    publish(current.copy(translatedNodes = current.translatedNodes + count))
                    yield()
                }
            } catch (cancelled: CancellationException) {
                if (cancelled is TimeoutCancellationException && token == session) {
                    if (automatic != null && current.stage == TranslationStage.IDLE) reset(restore = true)
                    else fail(if (current.stage == TranslationStage.DOWNLOADING) TranslationError.MODEL else TranslationError.TRANSLATION)
                } else throw cancelled
            } catch (_: Exception) {
                if (token == session) {
                    if (automatic != null && current.stage == TranslationStage.IDLE) reset(restore = true)
                    else fail(if (current.stage == TranslationStage.DOWNLOADING) TranslationError.MODEL else TranslationError.TRANSLATION)
                }
            } finally {
                backend?.close()
            }
        }
    }

    private suspend fun translateText(client: TextTranslationBackend, text: String): String {
        val start = text.indexOfFirst { !it.isWhitespace() }
        val end = text.indexOfLast { !it.isWhitespace() } + 1
        if (start < 0) return text
        val content = text.substring(start, end)
        // Bounded requests also handle unusually long paragraphs without flattening their DOM.
        val translated = buildString {
            var offset = 0
            while (offset < content.length) {
                var limit = minOf(offset + 2500, content.length)
                if (limit < content.length) {
                    if (Character.isHighSurrogate(content[limit - 1])) limit--
                    val boundary = content.lastIndexOf(' ', limit - 1)
                    if (boundary > offset + 1250) limit = boundary + 1
                }
                val chunk = content.substring(offset, limit)
                val trailing = chunk.takeLastWhile { it.isWhitespace() }
                append(withTimeout(30_000) { client.translate(chunk.trimEnd()) })
                append(trailing)
                offset = limit
            }
        }
        return text.substring(0, start) + translated + text.substring(end)
    }

    private fun fail(error: TranslationError) { publish(current.copy(stage = TranslationStage.ERROR, error = error)) }

    /** Reset synchronously invalidates callbacks, including downloads that finish after navigation. */
    fun reset(restore: Boolean) {
        val old = token
        token = null
        job?.cancel(); job = null
        if (restore && old != null) view.evaluateJavascript(
            "(() => { const t = window.__nagiTranslation; if (t && t.token === ${JSONObject.quote(old)}) t.restore(); })()", null)
        publish(PageTranslationState())
    }

    private suspend fun call(session: String, expression: String): String? {
        currentCoroutineContext().ensureActive()
        if (token != session) throw CancellationException()
        val result = evaluate("(() => { const t = window.__nagiTranslation; return t && t.token === ${JSONObject.quote(session)} ? t.$expression : null; })()")
        if (token != session) throw CancellationException()
        return if (result == "null") null else JSONArray("[$result]").opt(0)?.toString()
    }

    private suspend fun evaluate(source: String): String = withTimeout(10_000) {
        suspendCancellableCoroutine { continuation ->
            view.evaluateJavascript(source) { if (continuation.isActive) continuation.resume(it ?: "null") }
        }
    }
}
