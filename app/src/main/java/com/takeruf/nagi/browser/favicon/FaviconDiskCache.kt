package com.takeruf.nagi.browser.favicon

import java.io.File
import java.security.MessageDigest

/** A legacy/unclassified icon is only a fallback, never evidence of either theme. */
data class FaviconVariants(val light: String? = null, val dark: String? = null, val fallback: String? = null) {
    fun exact(darkTheme: Boolean): String? = if (darkTheme) dark else light
    fun select(darkTheme: Boolean): String? = exact(darkTheme) ?: fallback
    internal fun any(): String? = fallback ?: light ?: dark
}

/** Small, lazy disk index. Call on IO; all file changes and pruning share one lock. */
internal class FaviconDiskCache(
    private val directory: File,
    private val maxOrigins: Int = 256,
    private val maxBytes: Long = 8L * 1024 * 1024,
) {
    private val indexPattern = Regex("origin-([0-9a-f]{64})(?:-(?:light|dark))?\\.txt")
    private var maintained = false

    @Synchronized fun read(origin: String): FaviconVariants {
        if (!maintained) { trim(); maintained = true }
        fun path(dark: Boolean?): String? {
            val index = index(origin, dark)
            return readPath(index)?.also { index.setLastModified(System.currentTimeMillis()) }
        }
        return FaviconVariants(path(false), path(true), path(null))
    }

    @Synchronized fun contains(origin: String): Boolean = listOf(false, true, null).any { readPath(index(origin, it)) != null }

    @Synchronized fun hasNative(origin: String, dark: Boolean): Boolean = runCatching {
        val index = index(origin, dark)
        readPath(index) != null && index.readLines().getOrNull(1) == "webview"
    }.getOrDefault(false)

    @Synchronized fun write(origin: String, dark: Boolean?, bytes: ByteArray, native: Boolean = false): String? {
        if (bytes.isEmpty() || bytes.size > minOf(maxBytes, 131_072L)) return null
        directory.mkdirs()
        // Content addressing both shares identical variants and invalidates Coil's image keys.
        val image = File(directory, "${hash(bytes)}.png")
        if (!image.isFile) atomicWrite(image, bytes)
        val record = image.absolutePath + if (native && dark != null) "\nwebview" else ""
        atomicWrite(index(origin, dark), record.toByteArray(Charsets.UTF_8))
        trim()
        maintained = true
        return image.absolutePath.takeIf { image.isFile }
    }

    @Synchronized fun clear() {
        directory.listFiles()?.filter { indexPattern.matches(it.name) || it.extension in setOf("png", "tmp") }
            ?.forEach { it.delete() }
    }

    private fun index(origin: String, dark: Boolean?) = File(directory,
        "origin-${hash(origin.toByteArray(Charsets.UTF_8))}${when (dark) { true -> "-dark"; false -> "-light"; null -> "" }}.txt")

    private fun readPath(index: File): String? = runCatching {
        // Only paths managed by this cache can be referenced by an index.
        val image = File(index.takeIf { it.isFile && it.length() <= 4096 }?.readLines()?.firstOrNull() ?: return null)
        image.absolutePath.takeIf { image.parentFile == directory && image.extension == "png" && image.isFile }
    }.getOrNull()

    private fun atomicWrite(target: File, bytes: ByteArray) {
        val temporary = File.createTempFile("favicon-", ".tmp", directory)
        try { temporary.writeBytes(bytes); check(temporary.renameTo(target)) }
        finally { temporary.delete() }
    }

    private fun trim() {
        val files = directory.listFiles().orEmpty()
        val indexes = files.filter { indexPattern.matches(it.name) }.filter {
            if (readPath(it) == null) { it.delete(); false } else true
        }
        val groups = indexes.groupBy { indexPattern.matchEntire(it.name)!!.groupValues[1] }
            .values.sortedBy { group -> group.maxOf { it.lastModified() } }.toMutableList()
        val images = files.filter { it.extension == "png" }.associateBy { it.absolutePath }.toMutableMap()
        val references = indexes.mapNotNull(::readPath).groupingBy { it }.eachCount().toMutableMap()
        images.keys.toList().filter { it !in references }.forEach { images.remove(it)?.delete() }
        var bytes = images.values.sumOf { it.length() } + indexes.sumOf { it.length() }
        while (groups.size > maxOrigins || bytes > maxBytes) {
            val oldest = groups.removeFirstOrNull() ?: break
            oldest.forEach { index ->
                val path = readPath(index)
                bytes -= index.length()
                index.delete()
                if (path != null) {
                    val count = references.getValue(path) - 1
                    references[path] = count
                    if (count == 0) images.remove(path)?.let { bytes -= it.length(); it.delete() }
                }
            }
        }
        files.filter { it.extension == "tmp" }.forEach { it.delete() }
    }

    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
