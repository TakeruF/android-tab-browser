package com.takeruf.nagi

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.text.TextRunShaper
import android.os.LocaleList
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Synthetic native-rendering control; never reads page text, browser storage, or account data. */
class NativeFontFallbackTest {
    @Test
    @SdkSuppress(minSdkVersion = 31)
    fun inspectNativeJapaneseFallback() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue("Opt-in native font diagnostic only",
            InstrumentationRegistry.getArguments().getString("nativeFontProbe") == "true")
        val output = File(instrumentation.targetContext.getExternalFilesDir(null),
            "font-compatibility/native-fallback.json")
        output.parentFile!!.mkdirs()
        output.writeText(JSONObject().put("status", "in_progress").toString())
        try {
            val results = JSONArray()
            val families = linkedMapOf(
                "sans-serif" to Typeface.create("sans-serif", Typeface.NORMAL),
                "serif" to Typeface.create("serif", Typeface.NORMAL),
                "monospace" to Typeface.create("monospace", Typeface.NORMAL))
            val jpFile = File("/system/fonts/NotoSansCJK-Regular.ttc")
            if (jpFile.isFile) families["direct-system-jp-file"] =
                Typeface.Builder(jpFile).setTtcIndex(0).build()
            val samples = linkedMapOf("all" to "日本語漢字ひらがなカタカナ",
                "han" to "日本語漢字", "kana" to "ひらがなカタカナ")
            for (locale in listOf("default", "ja", "zh-CN", "en-US")) {
                for ((family, typeface) in families) {
                    val paint = Paint().apply {
                        this.typeface = typeface
                        textSize = 32f
                        if (locale != "default") textLocales = LocaleList.forLanguageTags(locale)
                    }
                    for ((sample, text) in samples) {
                        val glyphs = TextRunShaper.shapeTextRun(text, 0, text.length,
                            0, text.length, 0f, 0f, false, paint)
                        val counts = linkedMapOf<Pair<String, Int>, Int>()
                        var present = true
                        for (index in 0 until glyphs.glyphCount()) {
                            val font = glyphs.getFont(index)
                            // Only a font basename and collection index are retained.
                            val key = (font.file?.name ?: "memory-font") to font.ttcIndex
                            counts[key] = (counts[key] ?: 0) + 1
                            present = present && glyphs.getGlyphId(index) != 0
                        }
                        results.put(JSONObject().put("localeCase", locale)
                            .put("effectiveLocales", paint.textLocales.toLanguageTags())
                            .put("familyCase", family).put("sample", sample)
                            .put("glyphCount", glyphs.glyphCount()).put("allGlyphsPresent", present)
                            .put("fonts", JSONArray().apply {
                                for ((font, count) in counts) put(JSONObject()
                                    .put("file", font.first).put("ttcIndex", font.second)
                                    .put("glyphCount", count))
                            }))
                        assertEquals("Every fixed CJK sample character must be shaped", text.length, glyphs.glyphCount())
                        assertTrue("Fixed sample must not contain missing glyphs", present)
                    }
                }
            }
            output.writeText(JSONObject().put("status", "complete")
                .put("directSystemJpFileAvailable", jpFile.isFile).put("cases", results).toString(2))
        } catch (failure: Throwable) {
            output.writeText(JSONObject().put("status", "failed")
                .put("reason", "native_font_diagnostic_failed").toString())
            throw failure
        }
    }
}
