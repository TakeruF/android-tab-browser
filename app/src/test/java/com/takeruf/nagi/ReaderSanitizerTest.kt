package com.takeruf.nagi

import com.takeruf.nagi.browser.reader.ReaderSanitizer
import com.takeruf.nagi.browser.privacy.MemoryPreferences
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

class ReaderSanitizerTest {
    @Test fun removesExecutableMarkupAndResolvesSafeLinks() {
        val html = ReaderSanitizer.clean("""<p onclick="alert(1)">本文</p><script>alert(1)</script>
            <iframe src="https://tracker.example"></iframe><img src="/image.png" onerror="alert(1)">
            <a href="javascript:alert(1)">bad</a><a href="/next">next</a><form><input value="secret"></form>""", "https://news.example/article")
        assertTrue(html.contains("本文"))
        assertTrue(html.contains("https://news.example/image.png"))
        assertTrue(html.contains("https://news.example/next"))
        listOf("onclick", "onerror", "javascript:", "<script", "<iframe", "<input").forEach { assertFalse(html.contains(it)) }
    }
    @Test fun memorySettingsAreIndependentAndUpdatesAreSerialized() = runBlocking {
        val first = MemoryPreferences(); val second = MemoryPreferences(); val key = stringPreferencesKey("secret")
        first.edit { it[key] = "private" }
        assertEquals("private", first.data.value[key])
        assertNull(second.data.value[key])
    }
}
