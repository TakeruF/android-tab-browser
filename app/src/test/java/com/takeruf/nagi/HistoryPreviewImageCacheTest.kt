package com.takeruf.nagi

import android.graphics.Bitmap
import com.takeruf.nagi.browser.engine.HistoryPreviewImageCache
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistoryPreviewImageCacheTest {
    @Test fun ordinaryPhoneAndTabletViewportsKeepTheirNativeResolution() {
        val cache = HistoryPreviewImageCache(64L * 1024 * 1024)
        assertEquals(1080 to 1920, cache.dimensions(1080, 1920))
        assertEquals(2560 to 1600, cache.dimensions(2560, 1600))
        val (width, height) = cache.dimensions(3840, 2160)
        assertTrue(width.toLong() * height <= cache.maxImagePixels)
        assertTrue(width > 2600)
        assertEquals(3840.0 / 2160, width.toDouble() / height, 0.003)
    }
    @Test fun byteBudgetEvictsLeastRecentlyUsedImagesAcrossTabsWithoutRecyclingDisplayedImages() {
        val cache = HistoryPreviewImageCache(128)
        val first = Any(); val second = Any()
        val a = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val b = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val c = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        cache.put(first, "a", a); cache.put(second, "b", b)
        assertSame(a, cache.get(first, "a"))
        cache.put(first, "c", c)
        assertNull(cache.get(second, "b"))
        assertFalse(b.isRecycled)
        assertEquals(128L, cache.byteCount)
        cache.clear(first)
        assertEquals(0L, cache.byteCount)
        assertFalse(a.isRecycled)
    }
    @Test fun clearingOneWorkspaceDoesNotClearAnotherOwnersImages() {
        val cache = HistoryPreviewImageCache(128)
        val normal = Any(); val privateOwner = Any()
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        cache.put(normal, 1, bitmap); cache.put(privateOwner, 1, bitmap)
        cache.clear(privateOwner)
        assertSame(bitmap, cache.get(normal, 1))
        assertNull(cache.get(privateOwner, 1))
        assertEquals(64L, cache.byteCount)
    }
}
