package com.takeruf.nagi.browser.blocking

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mlm.adblock.AdblockEngine
import org.mlm.adblock.RequestTypeMapper
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Request matching and ABP parsing belong to Brave's engine, never to Nagi. */
class AdBlocker(context: Context, scope: CoroutineScope) {
    private data class FilterList(val name: String, val url: String, val header: String)
    private val lists = listOf(
        FilterList("easylist.txt", "https://easylist.to/easylist/easylist.txt", "[Adblock Plus"),
        FilterList("easyprivacy.txt", "https://easylist.to/easylist/easyprivacy.txt", "[Adblock Plus"),
        FilterList("adguard-japanese.txt", "https://filters.adtidy.org/extension/chromium/filters/7.txt", "! Checksum:"),
    )
    private val directory = File(context.filesDir, "adblock-lists")
    private fun cachedRules(context: Context) = lists.joinToString("\n") { list ->
        File(directory, list.name).takeIf { it.isFile }?.readText()
            ?: context.assets.open("adblock/${list.name}").bufferedReader().use { it.readText() }
    } + "\n" + context.assets.open("adblock/adguard-webview-compat.txt").bufferedReader().use { it.readText() }
    private val initialized = CountDownLatch(1)
    @Volatile private var engine: AdblockEngine? = null
    private val mutableStatus = MutableStateFlow("loading")
    val status = mutableStatus.asStateFlow()
    init {
        scope.launch(Dispatchers.IO) {
            try {
                val rules = cachedRules(context)
                val created = AdblockEngine.create()
                if (created.loadFilterList(rules)) { engine = created; mutableStatus.value = "ready" }
                else { created.close(); mutableStatus.value = "failed" }
            } catch (_: Throwable) { mutableStatus.value = "failed" }
            finally { initialized.countDown() }
            // Filter updates carry no page URLs, cookies or history. Keep the offline copy
            // if an update is unreachable or malformed. Never load scriptlets from a list.
            if (engine != null) {
                var changed = false
                lists.forEach { list ->
                    val file = File(directory, list.name)
                    if (System.currentTimeMillis() - file.lastModified() >= 4 * 24 * 60 * 60_000L) {
                        runCatching {
                            val connection = URL(list.url).openConnection() as HttpsURLConnection
                            connection.connectTimeout = 5_000; connection.readTimeout = 5_000
                            val bytes = try { connection.inputStream.use { it.readBytesBounded() } }
                                finally { connection.disconnect() }
                            val text = bytes.toString(Charsets.UTF_8)
                            require(bytes.size > 1024 && text.startsWith(list.header))
                            if (list.name == "adguard-japanese.txt") require(text.lineSequence().any {
                                it.trim() == "! Title: AdGuard Japanese filter"
                            })
                            directory.mkdirs()
                            val temporary = File.createTempFile("filter-", ".tmp", directory)
                            try { temporary.writeBytes(bytes); check(temporary.renameTo(file)) }
                                finally { temporary.delete() }
                            changed = true
                        }
                    }
                }
                if (changed) engine?.loadFilterList(cachedRules(context))
            }
        }
    }

    private fun java.io.InputStream.readBytesBounded(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) return output.toByteArray()
            require(output.size() + count <= 5_000_000)
            output.write(buffer, 0, count)
        }
    }

    fun intercept(request: WebResourceRequest, sourceUrl: String): WebResourceResponse? {
        if (request.isForMainFrame || request.url.scheme !in setOf("http", "https")) return null
        // WebView invokes interception on a worker. The first page also gets the bundled rules.
        initialized.await(10, TimeUnit.SECONDS)
        fun header(name: String) = request.requestHeaders.entries.firstOrNull { it.key.equals(name, true) }?.value
        val destination = header("Sec-Fetch-Dest")?.let { if (it == "style") "stylesheet" else it }
        val extensionType = RequestTypeMapper.from(request.url, null)
        // Some WebView versions supply the navigation's broad Accept header for scripts.
        // Prefer Fetch Metadata and a known file type to that ambiguous header.
        val type = when {
            destination != null && destination != "empty" -> RequestTypeMapper.from(request.url, destination)
            extensionType != "other" -> extensionType
            else -> RequestTypeMapper.from(request.url, header("Accept"))
        }
        if (engine?.shouldBlock(request.url.toString(), sourceUrl, type) != true) return null
        return WebResourceResponse("text/plain", "UTF-8", 200, "OK",
            mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0)))
    }

    fun cosmeticCss(url: String): String = engine?.cosmeticResources(url)?.css().orEmpty()
}
