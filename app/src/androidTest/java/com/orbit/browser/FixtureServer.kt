package com.orbit.browser

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.util.concurrent.Executors

/** Device-local HTTP fixture. Integration tests never depend on a public website. */
class FixtureServer : AutoCloseable {
    private val socket = ServerSocket(0)
    private val executor = Executors.newSingleThreadExecutor()
    val origin = "http://127.0.0.1:${socket.localPort}"
    init { executor.execute {
        while (!socket.isClosed) runCatching {
            socket.accept().use { client ->
                val reader = client.getInputStream().bufferedReader()
                val path = reader.readLine()?.split(' ')?.getOrNull(1) ?: "/one"
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = reader.readLine()
                    if (line.isNullOrEmpty()) break
                    headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim()
                }
                if (path == "/favicon.ico" || path == "/custom-icon.png") {
                    val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(if (path == "/favicon.ico") Color.MAGENTA else Color.CYAN)
                    val bytes = ByteArrayOutputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray()
                    }
                    bitmap.recycle()
                    client.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray())
                    client.getOutputStream().write(bytes)
                    return@use
                }
                if (path.startsWith("/download")) {
                    val data = "Orbit download fixture".toByteArray()
                    client.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Disposition: attachment; filename=orbit.txt\r\nContent-Length: ${data.size}\r\nConnection: close\r\n\r\n").toByteArray())
                    client.getOutputStream().write(data)
                    return@use
                }
                val body = if (path.startsWith("/icon-page")) "<html><head><title>Favicon Fixture</title><link rel=\"icon\" href=\"/custom-icon.png\"></head><body><h1>Favicon fixture</h1></body></html>"
                else if (path.startsWith("/ua")) "<html><head><title>Fixture ${if (headers["user-agent"].orEmpty().contains("Mobile")) "Mobile" else "Desktop"}</title></head><body>User agent fixture</body></html>"
                else if (path.startsWith("/scroll")) """
                    <html><head><title>Fixture Scroll</title><meta name="viewport" content="width=device-width,initial-scale=1,minimum-scale=1,maximum-scale=1"></head>
                    <body style="margin:0;width:4000px;height:4000px;background:linear-gradient(#f5f8f4,#244435)">
                    <div id="nested" style="position:absolute;left:20px;top:20px;width:220px;height:220px;overflow:scroll">
                      <div style="width:2000px;height:2000px;background:linear-gradient(90deg,#dce8de,#82a48c)">Nested scroll area</div>
                    </div>
                    <script>window.wheelCount=0;addEventListener('wheel',()=>window.wheelCount++);</script>
                    </body></html>
                """
                else if (path.startsWith("/two")) """
                    <html><head><title>Fixture Two</title><meta name="viewport" content="width=device-width,initial-scale=1"></head>
                    <body><h1>Second page</h1><p>Orbit split view fixture.</p></body></html>
                """ else """
                    <html><head><title>Fixture One</title><meta name="viewport" content="width=device-width,initial-scale=1"></head>
                    <body style="font:24px sans-serif;padding:30px;background:#f5f8f4;color:#244435">
                    <h1>Orbit browser fixture</h1><p>JavaScript, storage and navigation.</p>
                    <a id="next" href="/two">Open second page</a><br><br>
                    <a id="popup" href="/two" target="_blank">Open popup</a><br><br>
                    <input type="file" id="upload"><p>Find this needle in the page.</p>
                    <button id="fullscreen" onclick="document.body.requestFullscreen()">Fullscreen</button>
                    <textarea id="editor" aria-label="CJK editor" style="width:90%;height:100px"></textarea>
                    <script>localStorage.setItem('orbit','stored');document.cookie='orbit=cookie;path=/';
                    let db=indexedDB.open('orbit-fixture');db.onsuccess=()=>window.idbReady=true;</script>
                    </body></html>
                """
                val bytes = body.toByteArray()
                client.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray())
                client.getOutputStream().write(bytes)
            }
        }
    } }
    override fun close() { socket.close(); executor.shutdownNow() }
}
