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
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.TimeUnit

/** App-wide origin cache: favorites, tabs and history share successfully decoded PNGs. */
class FaviconStore internal constructor(private val context: Context) {
    private val cache = FaviconDiskCache(File(context.filesDir, "favicons"))
    private val loader = ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build()
    private val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
    private val userAgent = android.webkit.WebSettings.getDefaultUserAgent(context)
    private val locks = Array(32) { Mutex() }
    private val observations = object : LinkedHashMap<String, Long>(768, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > 768
    }
    private val observationSequence = AtomicLong()
    private val failures = object : LinkedHashMap<String, Long>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > 256
    }
    private val clearRevision = AtomicLong()
    private val diskLock = Mutex()
    private val mutableIcons = MutableStateFlow<Map<String, FaviconVariants>>(emptyMap())
    val icons = mutableIcons.asStateFlow()

    suspend fun save(siteUrl: String, bitmap: Bitmap, dark: Boolean? = null): String? {
        val origin = faviconOrigin(siteUrl) ?: return null
        val generation = clearRevision.get()
        val key = "$origin|$dark"
        val revision = synchronized(observations) { observationSequence.incrementAndGet().also { observations[key] = it } }
        return withContext(Dispatchers.IO) {
            diskLock.withLock {
                if (generation == clearRevision.get() && synchronized(observations) { observations[key] } == revision)
                    persist(origin, bitmap, dark, native = true) else null
            }
        }
    }

    /** UI calls use an unclassified fallback; only a live document supplies [dark]. */
    suspend fun resolve(siteUrl: String?, savedPath: String? = null, declared: List<String> = emptyList(), dark: Boolean? = null): String? {
        val origin = faviconOrigin(siteUrl) ?: return savedPath
        val generation = clearRevision.get()
        val key = "$origin|$dark"
        val revision = synchronized(observations) { observations[key] }
        return locks[(origin.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            withContext(Dispatchers.IO) {
                if (generation != clearRevision.get()) return@withContext null
                val variants = diskLock.withLock { cache.read(origin).also { publish(origin, it) } }
                val cached = if (dark == null) variants.any() else variants.select(dark)
                // A decoded WebView variant wins over independent fetching. Loading an index
                // must never relabel the previous theme's saved path as the new theme.
                if (declared.isEmpty() && cached != null) return@withContext cached
                val failureKey = "$origin|$dark"
                if (synchronized(failures) { failures[failureKey] }?.let { SystemClock.elapsedRealtime() - it < 300_000 } == true)
                    return@withContext cached
                suspend fun persistCandidate(bitmap: Bitmap, theme: Boolean?): String? {
                    currentCoroutineContext().ensureActive()
                    return diskLock.withLock {
                        if (generation != clearRevision.get()) null
                        else if (theme == dark && synchronized(observations) { observations[key] } != revision)
                            cache.read(origin).let { if (dark == null) it.any() else it.select(dark) }
                        else persist(origin, bitmap, theme)
                    }
                }
                // Cancellation is propagated; a failed candidate never prevents the next one.
                suspend fun decode(source: Any): Bitmap? = try {
                    (loader.execute(ImageRequest.Builder(context).data(source).size(64).build()) as? SuccessResult)
                        ?.image?.toBitmap(64, 64)
                } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                suspend fun fetchIcon(url: String, theme: Boolean? = dark): String? {
                    currentCoroutineContext().ensureActive()
                    if (generation != clearRevision.get()) return null
                    if (faviconOrigin(url) == null) return null
                    val bytes = fetch(url, 1_048_576)?.second ?: return null
                    // Independent SVG decoders don't evaluate WebView's prefers-color-scheme.
                    // Let WebView supply these bitmaps rather than caching the wrong CSS branch.
                    if (bytes.toString(Charsets.UTF_8).contains("prefers-color-scheme", ignoreCase = true)) return null
                    val bitmap = decode(ByteBuffer.wrap(bytes)) ?: return null
                    return persistCandidate(bitmap, theme)
                }
                if (declared.isEmpty() && !savedPath.isNullOrBlank() && savedPath !in listOf(variants.light, variants.dark)) {
                    val local = File(savedPath)
                    if (local.isFile) decode(local)?.let { return@withContext persistCandidate(it, null) }
                    else fetchIcon(savedPath, null)?.let { return@withContext it }
                }
                declared.filter { faviconOrigin(it) != null }.distinct().take(8).forEach {
                    fetchIcon(it)?.let { path -> return@withContext path }
                }
                if (generation != clearRevision.get()) return@withContext null
                if (declared.isNotEmpty() && cached != null) return@withContext cached
                // Only request the site's public origin, never a saved private path/query or a third-party icon service.
                val homepage = fetch(origin, 262_144, allowTruncated = true)
                if (homepage != null) faviconLinks(homepage.second.toString(Charsets.UTF_8), homepage.first).forEach {
                    fetchIcon(it, null)?.let { path -> return@withContext path }
                }
                defaultFaviconUrls(origin).forEach { fetchIcon(it, null)?.let { path -> return@withContext path } }
                if (generation == clearRevision.get()) {
                    synchronized(failures) { failures[failureKey] = SystemClock.elapsedRealtime() }
                    cached
                } else null
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

    /** Explicit site-data clearing invalidates pending work as well as both theme indexes. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        clearRevision.incrementAndGet()
        diskLock.withLock {
            cache.clear(); mutableIcons.value = emptyMap()
            synchronized(failures) { failures.clear() }
            synchronized(observations) { observations.clear() }
        }
    }

    private fun persist(origin: String, bitmap: Bitmap, dark: Boolean?, native: Boolean = false): String? = runCatching {
        val size = maxOf(bitmap.width, bitmap.height)
        val resized = if (size > 64) Bitmap.createScaledBitmap(bitmap,
            maxOf(1, bitmap.width * 64 / size), maxOf(1, bitmap.height * 64 / size), true) else bitmap
        val bytes = java.io.ByteArrayOutputStream().use {
            try { check(resized.compress(Bitmap.CompressFormat.PNG, 100, it)); it.toByteArray() }
            finally { if (resized !== bitmap) resized.recycle() }
        }
        val path = cache.write(origin, dark, bytes, native) ?: return null
        val variants = cache.read(origin)
        mutableIcons.update { icons -> icons.filterKeys { cache.contains(it) } + (origin to variants) }
        path
    }.getOrNull()

    private fun publish(origin: String, variants: FaviconVariants) {
        mutableIcons.update { icons ->
            // The disk policy bounds persisted origins; this also bounds missing-origin lookups.
            val remaining = if (origin !in icons && icons.size >= 256) icons - icons.keys.first() else icons
            remaining + (origin to variants)
        }
    }

    companion object {
        @Volatile private var instance: FaviconStore? = null
        fun get(context: Context): FaviconStore = instance ?: synchronized(this) {
            instance ?: FaviconStore(context.applicationContext).also { instance = it }
        }
    }
}
