package com.takeruf.nagi.browser.favicon

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.svg.SvgDecoder
import coil3.toBitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** App-wide origin cache: favorites, tabs and history share successfully decoded PNGs. */
class FaviconStore internal constructor(private val context: Context) {
    private val directory = File(context.filesDir, "favicons").apply { mkdirs() }
    private val loader = ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build()
    private val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
    private val userAgent = android.webkit.WebSettings.getDefaultUserAgent(context)
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val failedAt = ConcurrentHashMap<String, Long>()
    private val mutableIcons = MutableStateFlow<Map<String, String>>(emptyMap())
    val icons = mutableIcons.asStateFlow()

    suspend fun save(siteUrl: String, bitmap: Bitmap): String? {
        val origin = faviconOrigin(siteUrl) ?: return null
        return locks.getOrPut(origin) { Mutex() }.withLock {
            withContext(Dispatchers.IO) { persist(origin, bitmap) }
        }
    }

    suspend fun resolve(siteUrl: String?, savedPath: String? = null, declared: List<String> = emptyList()): String? {
        val origin = faviconOrigin(siteUrl) ?: return savedPath
        return locks.getOrPut(origin) { Mutex() }.withLock {
            withContext(Dispatchers.IO) {
                val cached = mutableIcons.value[origin] ?: runCatching { indexFile(origin).takeIf { it.isFile }?.readText() }.getOrNull()
                    ?.takeIf { File(it).isFile }?.also { publish(origin, it) }
                if (declared.isEmpty() && cached != null) return@withContext cached
                if (declared.isEmpty() && failedAt[origin]?.let { SystemClock.elapsedRealtime() - it < 300_000 } == true)
                    return@withContext cached
                // Cancellation is propagated; a failed candidate never prevents the next one.
                suspend fun decode(source: Any): Bitmap? = try {
                    (loader.execute(ImageRequest.Builder(context).data(source).size(64).build()) as? SuccessResult)
                        ?.image?.toBitmap(64, 64)
                } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                suspend fun fetchIcon(url: String): String? {
                    currentCoroutineContext().ensureActive()
                    if (faviconOrigin(url) == null) return null
                    val bytes = fetch(url, 1_048_576)?.second ?: return null
                    val bitmap = decode(ByteBuffer.wrap(bytes)) ?: return null
                    return persist(origin, bitmap)
                }
                if (declared.isEmpty() && !savedPath.isNullOrBlank()) {
                    val local = File(savedPath)
                    if (local.isFile) decode(local)?.let { return@withContext persist(origin, it) }
                    else fetchIcon(savedPath)?.let { return@withContext it }
                }
                declared.filter { faviconOrigin(it) != null }.distinct().take(8).forEach {
                    fetchIcon(it)?.let { path -> return@withContext path }
                }
                if (declared.isNotEmpty() && cached != null) return@withContext cached
                // Only request the site's public origin, never a saved private path/query or a third-party icon service.
                val homepage = fetch(origin, 262_144, allowTruncated = true)
                if (homepage != null) faviconLinks(homepage.second.toString(Charsets.UTF_8), homepage.first).forEach {
                    fetchIcon(it)?.let { path -> return@withContext path }
                }
                defaultFaviconUrls(origin).forEach { fetchIcon(it)?.let { path -> return@withContext path } }
                failedAt[origin] = SystemClock.elapsedRealtime()
                cached
            }
        }
    }

    private fun fetch(url: String, limit: Int, allowTruncated: Boolean = false): Pair<String, ByteArray>? = try {
        client.newCall(Request.Builder().url(url).header("User-Agent", userAgent).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            if (!allowTruncated && body.contentLength() > limit) return null
            val bytes = body.byteStream().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= limit) {
                    val count = input.read(buffer, 0, minOf(buffer.size, limit + 1 - output.size()))
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            if (bytes.size > limit && !allowTruncated) null
            else response.request.url.toString() to if (bytes.size > limit) bytes.copyOf(limit) else bytes
        }
    } catch (_: Exception) { null }

    private fun indexFile(origin: String) = File(directory, "origin-${hash(origin.toByteArray())}.txt")
    private fun persist(origin: String, bitmap: Bitmap): String? = runCatching {
        val bytes = java.io.ByteArrayOutputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)); it.toByteArray()
        }
        // Content-addressed paths avoid stale Coil memory entries when a site's icon changes.
        val file = File(directory, "${hash(bytes)}.png")
        if (!file.isFile) {
            val temporary = File.createTempFile("icon-", ".tmp", directory)
            try { temporary.writeBytes(bytes); check(temporary.renameTo(file)) } finally { temporary.delete() }
        }
        val index = indexFile(origin)
        val temporary = File.createTempFile("origin-", ".tmp", directory)
        try { temporary.writeText(file.absolutePath); check(temporary.renameTo(index)) } finally { temporary.delete() }
        publish(origin, file.absolutePath)
        failedAt.remove(origin)
        file.absolutePath
    }.getOrNull()

    private fun publish(origin: String, path: String) { mutableIcons.update { it + (origin to path) } }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        @Volatile private var instance: FaviconStore? = null
        fun get(context: Context): FaviconStore = instance ?: synchronized(this) {
            instance ?: FaviconStore(context.applicationContext).also { instance = it }
        }
    }
}
