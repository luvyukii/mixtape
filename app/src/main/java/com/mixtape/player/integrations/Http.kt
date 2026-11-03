package com.mixtape.player.integrations

import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

class HttpError(val code: Int, val body: String) : IOException("HTTP $code: ${body.take(300)}")

/** A minimal HTTP client; the app only needs a handful of JSON calls. */
object Http {

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        execute(url, "GET", headers, null)

    suspend fun postForm(url: String, form: Map<String, String>, headers: Map<String, String> = emptyMap()): String {
        val body = form.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        return execute(url, "POST", headers + ("Content-Type" to "application/x-www-form-urlencoded"), body)
    }

    private suspend fun execute(url: String, method: String, headers: Map<String, String>, body: String?): String =
        withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("Accept", "application/json")
                headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                if (body != null) {
                    conn.doOutput = true
                    conn.outputStream.use { it.write(body.toByteArray()) }
                }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw HttpError(code, text)
                text
            } finally {
                conn.disconnect()
            }
        }
}

/** PKCE helpers for browser sign-in (no client secret stored in the URL). */
object Pkce {
    private val random = SecureRandom()
    private const val FLAGS = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP

    fun randomString(bytes: Int = 48): String =
        Base64.encodeToString(ByteArray(bytes).also(random::nextBytes), FLAGS)

    fun challenge(verifier: String): String =
        Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)), FLAGS
        )
}

/** Reads a string field, treating JSON null and "" as missing. */
fun JSONObject.str(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }

fun Uri.Builder.params(vararg pairs: Pair<String, String>): Uri.Builder =
    apply { pairs.forEach { (k, v) -> appendQueryParameter(k, v) } }
