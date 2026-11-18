package com.takeruf.nagi

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.Date
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.security.auth.x500.X500Principal

/** Untrusted local TLS endpoint. Its short-lived test key never leaves the QA app's keystore. */
class TlsErrorFixture : AutoCloseable {
    private val alias = "nagi-error-fixture-${UUID.randomUUID()}"
    private val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val executor = Executors.newCachedThreadPool()
    private val socket: SSLServerSocket
    val attempts = AtomicInteger()
    val failures = CopyOnWriteArrayList<String>()
    val origin get() = "https://localhost:${socket.localPort}"
    init {
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply {
            initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(2048).setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256,
                    KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1, KeyProperties.ENCRYPTION_PADDING_NONE)
                .setCertificateSubject(X500Principal("CN=localhost"))
                .setCertificateSerialNumber(BigInteger.ONE)
                .setCertificateNotBefore(Date(System.currentTimeMillis() - 60_000))
                .setCertificateNotAfter(Date(System.currentTimeMillis() + 86_400_000)).build())
            generateKeyPair()
        }
        val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, null) }
        socket = SSLContext.getInstance("TLS").apply { init(managers.keyManagers, null, null) }
            .serverSocketFactory.createServerSocket(0) as SSLServerSocket
        socket.enabledProtocols = arrayOf("TLSv1.2")
        executor.execute {
            while (!socket.isClosed) runCatching {
                val client = socket.accept()
                executor.execute { runCatching { client.use {
                    attempts.incrementAndGet()
                    it.soTimeout = 5_000
                    it.getInputStream().read()
                } }.onFailure { failures.add("${it.javaClass.simpleName}: ${it.message}") } }
            }
        }
    }
    override fun close() {
        socket.close(); executor.shutdownNow(); store.deleteEntry(alias)
    }
}
