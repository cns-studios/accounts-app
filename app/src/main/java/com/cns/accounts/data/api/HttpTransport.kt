package com.cns.accounts.data.api

import android.os.Build
import com.cns.accounts.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** A request body: JSON, or a single-file multipart upload (the only kind auth-v2 accepts). */
sealed interface RequestBody {
    data class Json(val json: JSONObject) : RequestBody
    data class MultipartFile(
        val fieldName: String,
        val fileName: String,
        val contentType: String,
        val bytes: ByteArray,
    ) : RequestBody
}

class HttpResponse(val status: Int, val body: String) {
    val isSuccessful: Boolean get() = status in 200..299

    fun json(): JSONObject = if (body.isBlank()) JSONObject() else try {
        JSONObject(body)
    } catch (e: JSONException) {
        throw ApiException(status, "bad_response", "Unexpected response from the server")
    }
}

/**
 * The single low-level hop to the CNS auth API (auth-v2). Kept on plain HttpURLConnection +
 * org.json so the app doesn't pull in an HTTP/serialization stack for a dozen JSON endpoints.
 * Throws [IOException] for transport failures; HTTP error statuses come back as a response.
 */
class HttpTransport(private val baseUrl: String = BuildConfig.CNS_API_BASE_URL) {

    // auth-v2 derives the session/activity "device" name from this (it looks for "android"), so
    // the app's own sessions show up as "Android" in the web dashboard and the History tab.
    private val userAgent = "CNSAccounts/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE}; ${Build.MODEL})"

    suspend fun execute(
        method: String,
        path: String,
        body: RequestBody? = null,
        bearerToken: String? = null,
    ): HttpResponse = withContext(Dispatchers.IO) {
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TimeoutMillis
            readTimeout = TimeoutMillis
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", userAgent)
            if (bearerToken != null) setRequestProperty("Authorization", "Bearer $bearerToken")
        }
        try {
            when (body) {
                is RequestBody.Json -> connection.writeBody("application/json; charset=utf-8", body.json.toString().toByteArray())
                is RequestBody.MultipartFile -> {
                    val boundary = "cns-${UUID.randomUUID()}"
                    connection.writeBody("multipart/form-data; boundary=$boundary", body.encode(boundary))
                }
                null -> Unit
            }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            HttpResponse(status, text)
        } finally {
            connection.disconnect()
        }
    }

    private fun HttpURLConnection.writeBody(contentType: String, bytes: ByteArray) {
        doOutput = true
        setRequestProperty("Content-Type", contentType)
        setFixedLengthStreamingMode(bytes.size)
        outputStream.use { it.write(bytes) }
    }

    private fun RequestBody.MultipartFile.encode(boundary: String): ByteArray {
        val out = ByteArrayOutputStream(bytes.size + 512)
        val header = "--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"$fieldName\"; filename=\"$fileName\"\r\n" +
            "Content-Type: $contentType\r\n\r\n"
        out.write(header.toByteArray())
        out.write(bytes)
        out.write("\r\n--$boundary--\r\n".toByteArray())
        return out.toByteArray()
    }

    private companion object {
        const val TimeoutMillis = 15_000
    }
}
