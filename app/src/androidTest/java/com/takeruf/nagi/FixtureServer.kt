package com.takeruf.nagi

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.util.concurrent.Executors

/** Device-local HTTP fixture. Integration tests never depend on a public website. */
class FixtureServer(private val rootIconAvailable: Boolean = true, private val homeIconMarkup: String = "",
    private val useIcoContainer: Boolean = false) : AutoCloseable {
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
                if (path == "/stall") Thread.sleep(35_000)
                if (path == "/favicon.ico" && !rootIconAvailable) {
                    client.getOutputStream().write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                    return@use
                }
                if (path == "/custom-icon.svg") {
                    val bytes = "<svg xmlns='http://www.w3.org/2000/svg' width='64' height='64'><rect width='64' height='64' fill='#00ffff'/></svg>".toByteArray()
                    client.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: image/svg+xml\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    client.getOutputStream().write(bytes)
                    return@use
                }
                if (path == "/favicon.ico" || path == "/custom-icon.png" || path == "/apple-touch-icon.png") {
                    val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(if (path == "/favicon.ico") Color.MAGENTA else Color.CYAN)
                    val png = ByteArrayOutputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray()
                    }
                    val bytes = if (path == "/favicon.ico" && useIcoContainer) {
                        java.nio.ByteBuffer.allocate(22 + png.size).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                            .putShort(0).putShort(1).putShort(1)
                            .put(32).put(32).put(0).put(0).putShort(1).putShort(32)
                            .putInt(png.size).putInt(22).put(png).array()
                    } else png
                    bitmap.recycle()
                    client.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: ${if (path == "/favicon.ico" && useIcoContainer) "image/x-icon" else "image/png"}\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray())
                    client.getOutputStream().write(bytes)
                    return@use
                }
                if (path.startsWith("/download")) {
                    val data = "Nagi download fixture".toByteArray()
                    client.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Disposition: attachment; filename=nagi.txt\r\nContent-Length: ${data.size}\r\nConnection: close\r\n\r\n").toByteArray())
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
                    <body><h1>Second page</h1><p>Nagi split view fixture.</p></body></html>
                """ else """
                    <html><head><title>Fixture One</title>$homeIconMarkup<meta name="viewport" content="width=device-width,initial-scale=1"></head>
                    <body style="font:24px sans-serif;padding:30px;background:#f5f8f4;color:#244435">
                    <h1>Nagi browser fixture</h1><p>JavaScript, storage and navigation.</p>
                    <a id="next" href="/two">Open second page</a><br><br>
                    <a id="popup" href="/two" target="_blank">Open popup</a><br><br>
                    <img id="context-image" src="/custom-icon.png" width="64" height="64"><br>
                    <input type="file" id="upload"><p>Find this needle in the page.</p>
                    <button id="fullscreen" onclick="document.body.requestFullscreen()">Fullscreen</button>
                    <textarea id="editor" aria-label="CJK editor" style="width:90%;height:100px"></textarea>
                    <script>localStorage.setItem('nagi','stored');document.cookie='nagi=cookie;path=/';
                    let db=indexedDB.open('nagi-fixture');db.onsuccess=()=>window.idbReady=true;</script>
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
