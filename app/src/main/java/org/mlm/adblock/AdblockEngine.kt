// MPL-2.0, mlm-games/adblock-rs-kt-wrapper 0.1.3.
// Nagi modification (2026-10-10): block-body deserializeFrom for Kotlin 2.2 compatibility.
package org.mlm.adblock

import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Result of a network-request check, mirroring `adblock::blocker::BlockerResult`.
 *
 * @param matched whether the request should be blocked (after exceptions).
 * @param important an `important`-rule match (overrides exceptions).
 * @param redirect data: URL of a replacement resource to serve instead (e.g. a noop stub).
 * @param rewrittenUrl rewritten URL from `$removeparam`/`$redirect-rule`, if the URL changed.
 * @param exception an exception rule matched (request must NOT be blocked).
 */
data class BlockDecision(
    val matched: Boolean,
    val important: Boolean,
    val redirect: String?,
    val rewrittenUrl: String?,
    val exception: Boolean
)

/**
 * Cosmet filtering resources for a single page URL, produced by
 * [AdblockEngine.cosmeticResources].
 *
 * @param selectors CSS selectors that should be hidden (`display: none !important`).
 * @param js scriptlet JavaScript to inject at document start.
 */
data class CosmeticResources(
    val selectors: List<String>,
    val js: String
) {
    fun css(): String =
        selectors.joinToString("\n") { "$it { display: none !important; }" }
}

/**
 * Thin JNI wrapper around the brave/adblock-rust Engine.
 *
 * Thread-safe: the underlying engine is guarded by a Mutex on the Rust side
 * (the "single-thread" feature of the adblock crate is intentionally left off,
 * since WebView request interception happens on multiple threads).
 */
class AdblockEngine private constructor(private var nativePtr: Long) : AutoCloseable {

    private val closed = AtomicBoolean(false)

    companion object {
        init {
            System.loadLibrary("adblock_ffi")
        }

        fun create(): AdblockEngine = AdblockEngine(nativeCreate())

        @JvmStatic
        private external fun nativeCreate(): Long

        @JvmStatic
        private external fun nativeDestroy(ptr: Long)

        @JvmStatic
        private external fun nativeLoadFilterList(ptr: Long, rules: String): Boolean

        @JvmStatic
        private external fun nativeLoadResources(ptr: Long, resourcesJson: String): Boolean

        @JvmStatic
        private external fun nativeGetCosmeticResources(ptr: Long, url: String): String?

        @JvmStatic
        private external fun nativeCheckNetworkUrls(
            ptr: Long,
            url: String,
            sourceUrl: String,
            requestType: String
        ): String?

        @JvmStatic
        private external fun nativeSerialize(ptr: Long): ByteArray?

        @JvmStatic
        private external fun nativeDeserialize(ptr: Long, data: ByteArray): Boolean
    }

    /**
     * Loads filter list text (one or more lists joined with `\n`).
     * Replaces any previously loaded filters.
     */
    fun loadFilterList(rulesText: String): Boolean {
        check(!closed.get()) { "AdblockEngine is closed" }
        return nativeLoadFilterList(nativePtr, rulesText)
    }

    /**
     * @param requestType adblock-rust type: script|image|stylesheet|xmlhttprequest|
     *   media|font|websocket|other|document|subdocument|...
     */
    fun shouldBlock(url: String, sourceUrl: String, requestType: String): Boolean {
        return checkNetworkRequest(url, sourceUrl, requestType).matched
    }

    /**
     * Full result for a network request, including redirect/rewrite decisions.
     * Returns a non-blocking decision when the engine isn't ready or the check fails.
     */
    fun checkNetworkRequest(url: String, sourceUrl: String, requestType: String): BlockDecision {
        if (closed.get() || nativePtr == 0L) return BlockDecision(false, false, null, null, false)
        return try {
            val json = nativeCheckNetworkUrls(nativePtr, url, sourceUrl, requestType) ?: return BlockDecision(false, false, null, null, false)
            val obj = JSONObject(json)
            BlockDecision(
                matched = obj.optBoolean("matched"),
                important = obj.optBoolean("important"),
                redirect = if (obj.isNull("redirect")) null else obj.optString("redirect", null),
                rewrittenUrl = if (obj.isNull("rewritten_url")) null else obj.optString("rewritten_url", null),
                exception = obj.optBoolean("exception")
            )
        } catch (_: Throwable) {
            BlockDecision(false, false, null, null, false)
        }
    }

    /**
     * Loads scriptlet/redirect resources required by `+js(...)` and `$redirect` rules.
     * Must be called after [loadFilterList] or [deserializeFrom] (resources are not
     * part of the serialized engine binary).
     */
    fun loadResources(resourcesJson: String): Boolean {
        check(!closed.get()) { "AdblockEngine is closed" }
        return try {
            nativeLoadResources(nativePtr, resourcesJson)
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Cosmetic filtering resources (hide selectors + scriptlet JS) for a page URL.
     * Returns null if the engine is not ready or no rules apply.
     */
    fun cosmeticResources(url: String): CosmeticResources? {
        if (closed.get() || nativePtr == 0L) return null
        return try {
            val json = nativeGetCosmeticResources(nativePtr, url) ?: return null
            val obj = JSONObject(json)
            val selectorsArray = obj.optJSONArray("selectors")
            val selectors = if (selectorsArray == null) {
                emptyList()
            } else {
                (0 until selectorsArray.length()).map { selectorsArray.getString(it) }
            }
            CosmeticResources(selectors, obj.optString("js"))
        } catch (_: Throwable) {
            null
        }
    }

    fun serializeTo(file: File): Boolean = try {
        val bytes = nativeSerialize(nativePtr) ?: return false
        file.writeBytes(bytes)
        true
    } catch (_: Exception) {
        false
    }

    fun deserializeFrom(file: File): Boolean { return try {
        if (!file.exists()) return false
        nativeDeserialize(nativePtr, file.readBytes())
    } catch (_: Exception) {
        false
    } }

    override fun close() {
        if (closed.compareAndSet(false, true) && nativePtr != 0L) {
            nativeDestroy(nativePtr)
            nativePtr = 0L
        }
    }
}
