package com.takeruf.nagi.testing

import android.credentials.ClearCredentialStateException
import android.credentials.CreateCredentialException
import android.credentials.GetCredentialException
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import android.service.credentials.BeginCreateCredentialRequest
import android.service.credentials.BeginCreateCredentialResponse
import android.service.credentials.BeginGetCredentialRequest
import android.service.credentials.BeginGetCredentialResponse
import android.service.credentials.ClearCredentialStateRequest
import android.service.credentials.CredentialProviderService
import androidx.annotation.RequiresApi
import androidx.credentials.PublicKeyCredential
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger

/** Debug-only native dispatch probe. Never returns or stores a credential. */
@RequiresApi(34)
class PasskeyHarnessService : CredentialProviderService() {
    override fun onBeginGetCredential(request: BeginGetCredentialRequest, cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>) {
        val expected = expectedOrigin
        val caller = request.callingAppInfo
        if (expected != null) {
            receivedRequests.incrementAndGet()
            lastCallerMatched = caller != null && caller.packageName == packageName
            val expectedUri = URI(expected)
            val nativeUri = caller?.origin?.let { runCatching { URI(it) }.getOrNull() }
            val fixtureOrigin = expectedUri.scheme == "http" && expectedUri.host == "localhost" && expectedUri.port > 0
            val sameScheme = nativeUri?.scheme == expectedUri.scheme
            val sameHost = nativeUri?.host == expectedUri.host
            val samePort = nativeUri?.port == expectedUri.port
            val rootPath = nativeUri != null && (nativeUri.rawPath.isNullOrEmpty() || nativeUri.rawPath == "/")
            // Chromium 133 passes Origin-as-URL (including a root slash) to Credential
            // Manager. Compare the website origin tuple, rejecting any other URL data.
            lastOriginMatched = fixtureOrigin && nativeUri != null && sameScheme && sameHost && samePort &&
                rootPath && nativeUri.rawUserInfo == null && nativeUri.rawQuery == null && nativeUri.rawFragment == null
            if (fixtureOrigin) lastOriginProbe = "present=${caller?.origin != null}, scheme=$sameScheme, " +
                "host=$sameHost, port=$samePort, path=" + when {
                    nativeUri == null -> "invalid"
                    nativeUri.rawPath.isNullOrEmpty() -> "empty"
                    nativeUri.rawPath == "/" -> "root"
                    else -> "other"
                } + ", query=${nativeUri?.rawQuery != null}, fragment=${nativeUri?.rawFragment != null}"
            lastTypeMatched = request.beginGetCredentialOptions.any { it.type == PublicKeyCredential.TYPE_PUBLIC_KEY_CREDENTIAL }
            if (lastCallerMatched && lastOriginMatched && lastTypeMatched) matchingRequests.incrementAndGet()
        }
        // A no-match result exercises delivery back to WebAuthn without a vault, account,
        // fake assertion, custom picker or real-provider dependency.
        callback.onResult(BeginGetCredentialResponse.Builder().build())
    }

    override fun onBeginCreateCredential(request: BeginCreateCredentialRequest, cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>) {
        callback.onError(CreateCredentialException(CreateCredentialException.TYPE_NO_CREATE_OPTIONS))
    }

    override fun onClearCredentialState(request: ClearCredentialStateRequest, cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void, ClearCredentialStateException>) {
        callback.onResult(null)
    }

    companion object {
        // Counts only a test-owned localhost request with the expected native website origin.
        // No challenges, client data, account names, keys or assertions are retained.
        @Volatile var expectedOrigin: String? = null
        val matchingRequests = AtomicInteger()
        val receivedRequests = AtomicInteger()
        @Volatile var lastCallerMatched = false
        @Volatile var lastOriginMatched = false
        @Volatile var lastTypeMatched = false
        @Volatile var lastOriginProbe = "no-provider"
    }
}
