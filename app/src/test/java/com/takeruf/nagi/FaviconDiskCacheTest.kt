package com.takeruf.nagi

import com.takeruf.nagi.browser.favicon.FaviconDiskCache
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FaviconDiskCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private val origin = "https://example.com/"

    @Test fun variantsSurviveReopeningWithoutRelabelingTheOtherTheme() {
        val directory = temporary.newFolder()
        val cache = FaviconDiskCache(directory)
        val white = cache.write(origin, true, byteArrayOf(1), native = true)
        assertNull(cache.read(origin).select(false))
        assertEquals(white, cache.read(origin).select(true))
        val black = cache.write(origin, false, byteArrayOf(2), native = true)
        val reopened = FaviconDiskCache(directory)
        assertEquals(white, reopened.read(origin).select(true))
        assertEquals(black, reopened.read(origin).select(false))
        assertTrue(reopened.hasNative(origin, true))
        assertTrue(reopened.hasNative(origin, false))
    }

    @Test fun oldIndexRemainsUnclassifiedAndNewVariantTakesPriority() {
        val directory = temporary.newFolder()
        val cache = FaviconDiskCache(directory)
        val old = cache.write(origin, null, byteArrayOf(1))
        val reopened = FaviconDiskCache(directory)
        assertEquals(old, reopened.read(origin).select(true))
        assertEquals(old, reopened.read(origin).select(false))
        assertFalse(reopened.hasNative(origin, false))
        val dark = reopened.write(origin, true, byteArrayOf(2))
        assertEquals(dark, reopened.read(origin).select(true))
        assertEquals(old, reopened.read(origin).select(false))
        assertNull(reopened.read(origin).light)
    }

    @Test fun fixedIconsShareBytesAndReplacedIconsAreGarbageCollected() {
        val directory = temporary.newFolder()
        val cache = FaviconDiskCache(directory)
        val fixed = cache.write(origin, false, byteArrayOf(1))!!
        assertEquals(fixed, cache.write(origin, true, byteArrayOf(1)))
        assertEquals(1, directory.listFiles()!!.count { it.extension == "png" })
        cache.write(origin, false, byteArrayOf(2))
        assertTrue(File(fixed).isFile) // Dark still references it.
        cache.write(origin, true, byteArrayOf(2))
        assertFalse(File(fixed).exists())
        assertEquals(1, directory.listFiles()!!.count { it.extension == "png" })
    }

    @Test fun evictionRemovesAnOriginsVariantsTogetherAndRetainsSharedImages() {
        val directory = temporary.newFolder()
        val cache = FaviconDiskCache(directory, maxOrigins = 2)
        val shared = cache.write(origin, false, byteArrayOf(1))!!
        cache.write(origin, true, byteArrayOf(2))
        cache.write("https://second.test/", false, byteArrayOf(1))
        directory.listFiles()!!.filter { it.extension == "txt" && it.readText().substringBefore('\n') == shared }
            .forEach { it.setLastModified(1000) }
        // Both first-origin indexes must be oldest, including its distinct dark image.
        directory.listFiles()!!.filter { it.name.endsWith("-dark.txt") }.forEach { it.setLastModified(1000) }
        cache.read("https://second.test/")
        cache.write("https://third.test/", false, byteArrayOf(3))
        assertNull(cache.read(origin).any())
        assertEquals(shared, cache.read("https://second.test/").light)
        assertTrue(File(shared).isFile)
        assertNotNull(cache.read("https://third.test/").light)
    }

    @Test fun byteBudgetIncludesIndexesAndClearOrMissingFilesInvalidateEveryVariant() {
        val directory = temporary.newFolder()
        val cache = FaviconDiskCache(directory, maxBytes = 1600)
        cache.write(origin, true, ByteArray(700) { 1 })
        directory.listFiles()!!.filter { it.extension == "txt" }.forEach { it.setLastModified(1000) }
        val latest = cache.write("https://second.test/", false, ByteArray(700) { 2 })!!
        assertTrue(directory.listFiles()!!.sumOf { it.length() } <= 1600)
        assertNull(cache.read(origin).dark)
        File(latest).delete()
        assertNull(cache.read("https://second.test/").light)
        cache.write(origin, true, byteArrayOf(3))
        cache.clear()
        assertNull(cache.read(origin).any())
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun reopenedLegacyCacheIsPrunedLazilyOnFirstRead() {
        val directory = temporary.newFolder()
        val old = FaviconDiskCache(directory, maxOrigins = 10)
        repeat(4) { old.write("https://$it.test/", null, byteArrayOf(it.toByte())) }
        val bounded = FaviconDiskCache(directory, maxOrigins = 2)
        bounded.read("https://unused.test/")
        assertEquals(2, directory.listFiles()!!.count { it.extension == "txt" })
        assertEquals(2, directory.listFiles()!!.count { it.extension == "png" })
    }
}
