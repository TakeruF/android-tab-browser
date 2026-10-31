package com.takeruf.nagi

import org.json.JSONObject
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Loopback-only negative controls. Request headers, cookies and bodies are never recorded. */
internal class FontControlServer(private val latinFont: ByteArray, private val italicFont: ByteArray? = null) : AutoCloseable {
    private val socket = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
    private val executor = Executors.newCachedThreadPool()
    private val hits = ConcurrentHashMap<String, AtomicInteger>()
    val origin = "http://127.0.0.1:${socket.localPort}"
    fun counts() = JSONObject(hits.mapValues { it.value.get() })
    private val html = """
        <!doctype html><html lang="ja"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Font failure controls</title><style>
        @font-face{font-family:ProbeMissing;src:url('/missing.ttf');font-display:swap}
        @font-face{font-family:ProbeInvalid;src:url('/invalid.ttf');font-display:swap}
        @font-face{font-family:ProbeLatinOnly;src:url('/latin.ttf');unicode-range:U+0-7F;font-display:swap}
        @font-face{font-family:ProbeRule;src:url('/assets/ads/font-probe.ttf');unicode-range:U+0-7F;font-display:swap}
        p{font-size:32px}
        </style><body>
        <p id="control-baseline-sans" style="font-family:sans-serif">日本語漢字ひらがなカタカナ</p>
        <p id="control-baseline-serif" style="font-family:serif">日本語漢字ひらがなカタカナ</p>
        <p id="control-missing-sans" style="font-family:ProbeMissing,sans-serif">日本語漢字ひらがなカタカナ</p>
        <p id="control-missing-serif" style="font-family:ProbeMissing,serif">日本語漢字ひらがなカタカナ</p>
        <p id="control-invalid-sans" style="font-family:ProbeInvalid,sans-serif">日本語漢字ひらがなカタカナ</p>
        <p id="control-latin-only-latin" style="font-family:ProbeLatinOnly,sans-serif">ABC 123</p>
        <p id="control-latin-only-japanese" style="font-family:ProbeLatinOnly,sans-serif">日本語漢字ひらがなカタカナ</p>
        <p id="control-rule-latin" style="font-family:ProbeRule,sans-serif">ABC 123</p>
        </body></html>
    """.trimIndent().toByteArray()
    private val copyHtml = """
        <!doctype html><html lang="en"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Font style copying controls</title><style>
        @font-face{font-family:ProbeStyled;src:url('/latin.ttf');font-style:normal;font-weight:100 900}
        @font-face{font-family:ProbeStyled;src:url('/latin-italic.ttf');font-style:italic;font-weight:100 900}
        p{font-size:24px;font-family:sans-serif}
        #copy-reference-normal{font-family:ProbeStyled,sans-serif;font-size:20px;letter-spacing:2px}
        #copy-reference-italic{font-family:ProbeStyled,sans-serif;font-style:italic;font-stretch:75%;font-variation-settings:"wght" 720;
          font-feature-settings:"liga" 0;font-variant-ligatures:none;font-optical-sizing:none;font-synthesis:none}
        </style><body>
        <p id="copy-reference-normal" lang="en">ABC 123</p>
        <p id="copy-reference-italic" lang="en">ABC 123</p>
        <p id="copy-reference-japanese" lang="ja">日本語漢字ひらがなカタカナ</p>
        <p id="copy-reference-chinese" lang="zh-Hans">日本語漢字ひらがなカタカナ</p>
        </body></html>
    """.trimIndent().toByteArray()
    private val apiHtml = "<!doctype html><html lang='ja'><p id='api-reference' style='font-family:sans-serif'>日本語漢字ひらがなカタカナ</p></html>".toByteArray()
    init { executor.execute {
        while (!socket.isClosed) runCatching {
            val accepted = socket.accept()
            executor.execute { runCatching { accepted.use { client ->
                client.soTimeout = 5_000
                val reader = client.getInputStream().bufferedReader()
                val path = reader.readLine()?.split(' ')?.getOrNull(1)?.substringBefore('?') ?: return@use
                while (true) { if (reader.readLine().isNullOrEmpty()) break }
                val category = when (path) {
                    "/font-controls" -> "document"
                    "/font-copy-controls" -> "copy-document"
                    "/font-api-controls" -> "api-document"
                    "/missing.ttf" -> "missing"
                    "/invalid.ttf" -> "invalid"
                    "/latin.ttf" -> "latin"
                    "/latin-italic.ttf" -> "italic"
                    "/assets/ads/font-probe.ttf" -> "rule"
                    else -> "other"
                }
                hits.getOrPut(category) { AtomicInteger() }.incrementAndGet()
                val (status, mime, body) = when (category) {
                    "document" -> Triple("200 OK", "text/html; charset=utf-8", html)
                    "copy-document" -> Triple("200 OK", "text/html; charset=utf-8", copyHtml)
                    "api-document" -> Triple("200 OK", "text/html; charset=utf-8", apiHtml)
                    "latin", "rule" -> Triple("200 OK", "font/ttf", latinFont)
                    "italic" -> Triple("200 OK", "font/ttf", italicFont ?: ByteArray(0))
                    "invalid" -> Triple("200 OK", "font/ttf", "This is not a font".toByteArray())
                    else -> Triple("404 Not Found", "font/ttf", ByteArray(0))
                }
                client.getOutputStream().write(("HTTP/1.1 $status\r\nContent-Type: $mime\r\n" +
                    "Cache-Control: no-store\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray())
                client.getOutputStream().write(body)
            } } }
        }
    } }
    override fun close() { socket.close(); executor.shutdownNow() }
}
