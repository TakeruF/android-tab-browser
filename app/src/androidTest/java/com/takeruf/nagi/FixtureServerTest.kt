package com.takeruf.nagi

import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import org.junit.Assert.assertTrue
import org.junit.Test

class FixtureServerTest {
    @Test fun unusedBrowserPreconnectionDoesNotBlockTheNextRequest() {
        FixtureServer().use { server ->
            val endpoint = URL(server.origin)
            Socket(endpoint.host, endpoint.port).use {
                val request = URL("${server.origin}/ua-preconnect").openConnection() as HttpURLConnection
                request.connectTimeout = 2_000
                request.readTimeout = 2_000
                try {
                    assertTrue(request.inputStream.bufferedReader().use { it.readText() }.contains("Fixture Desktop"))
                } finally { request.disconnect() }
            }
        }
    }
}
