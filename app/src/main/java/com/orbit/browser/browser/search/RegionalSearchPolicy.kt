package com.orbit.browser.browser.search

import android.content.Context
import android.telephony.TelephonyManager
import com.orbit.browser.data.datastore.SettingsStore
import com.orbit.browser.data.repository.WorkspaceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

data class SearchRegion(val country: String, val source: String)

/** Country only: language, time zone and a VPN's presumed physical location are not inferred. */
object RegionalSearchPolicy {
    fun country(value: String?): String? = value?.trim()?.uppercase(Locale.ROOT)
        ?.takeIf { it in Locale.getISOCountries().toSet() }
    fun engine(country: String): String = if (country == "CN") "baidu" else "google"
    fun fallback(network: String?, sim: String?, locale: String?): SearchRegion? =
        country(network)?.let { SearchRegion(it, "Mobile network") }
            ?: country(sim)?.let { SearchRegion(it, "SIM") }
            ?: country(locale)?.let { SearchRegion(it, "Device region") }
}

class RegionalSearchDefaults(
    private val workspace: WorkspaceRepository,
    private val settings: SettingsStore,
    private val lookup: suspend () -> String?,
    private val fallback: () -> SearchRegion?,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    suspend fun refresh(force: Boolean = false) = mutex.withLock {
        workspace.ready.await()
        val prefs = settings.settings.first()
        if (!prefs.automaticSearchRegion) return@withLock
        val cacheAge = now() - prefs.searchRegionCheckedAt
        val lifetime = if (prefs.searchRegionSource == "IP") 86_400_000L else 3_600_000L
        if (!force && prefs.searchRegionCheckedAt > 0 && cacheAge in 0 until lifetime) return@withLock
        // Apply an offline fallback immediately on a fresh install; never block browser startup.
        val local = fallback()
        if (prefs.searchRegionCheckedAt == 0L && local != null) apply(local)
        val ip = try { RegionalSearchPolicy.country(lookup()) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
        val region = ip?.let { SearchRegion(it, "IP") } ?: local
        if (region != null) apply(region)
        else settings.update { if (it.automaticSearchRegion) it.copy(searchRegionSource = "Unavailable", searchRegionCheckedAt = now()) else it }
    }
    private suspend fun apply(region: SearchRegion) {
        val engine = RegionalSearchPolicy.engine(region.country)
        val available = workspace.dao.engines().any { it.id == engine }
        settings.update {
            // Recheck inside the atomic edit: a manual choice during the request wins.
            if (!it.automaticSearchRegion) it else it.copy(
                defaultSearchEngineId = if (available) engine else it.defaultSearchEngineId,
                searchRegionCountry = region.country, searchRegionSource = region.source,
                searchRegionCheckedAt = now())
        }
    }
}

class AndroidSearchRegion(private val context: Context) {
    fun fallback(): SearchRegion? {
        val phone = context.getSystemService(TelephonyManager::class.java)
        val network = runCatching { phone?.networkCountryIso }.getOrNull()
        val sim = runCatching { phone?.simCountryIso }.getOrNull()
        return RegionalSearchPolicy.fallback(network, sim, context.resources.configuration.locales[0].country)
    }
    suspend fun lookup(): String? = withContext(Dispatchers.IO) {
        // No query, browsing history, device ID or explicit IP is sent. The service sees the connection IP.
        val connection = URL("https://api.country.is/").openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 2000; connection.readTimeout = 2000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode != 200) return@withContext null
            val response = connection.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(4096)
                var count = 0
                while (count < buffer.size) {
                    val read = reader.read(buffer, count, buffer.size - count)
                    if (read < 0) break
                    count += read
                }
                String(buffer, 0, count)
            }
            RegionalSearchPolicy.country(JSONObject(response).optString("country"))
        } finally { connection.disconnect() }
    }
}
