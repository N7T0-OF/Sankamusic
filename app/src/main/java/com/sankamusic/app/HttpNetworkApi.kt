package com.sankamusic.app

import com.sankamusic.core.api.NetworkApi
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implémentation réelle de la [NetworkApi] (java.net + coroutines).
 * Utilisée par le client GitHub Releases de l'updater et par les plugins.
 *
 * Le User-Agent est obligatoire pour l'API GitHub (sinon HTTP 403).
 * ⚠️ Volontairement sans dépendance HTTP externe pour l'instant ; un client
 * OkHttp pourra la remplacer en Phase 2 si besoin.
 */
class HttpNetworkApi : NetworkApi {

    override suspend fun get(url: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = READ_TIMEOUT_MS

                val code = conn.responseCode
                val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""

                if (code !in 200..299) {
                    throw IllegalStateException("HTTP $code : ${body.take(200)}")
                }
                body
            } finally {
                conn.disconnect()
            }
        }
    }

    private companion object {
        const val USER_AGENT = "Sankamusic"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 15_000
    }
}
