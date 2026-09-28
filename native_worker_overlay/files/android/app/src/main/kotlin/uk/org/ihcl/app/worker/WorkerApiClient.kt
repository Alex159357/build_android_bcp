package uk.org.ihcl.app.worker

import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class WorkerApiClient(
    private val baseUrl: String,
    private val deviceId: String,
    private val deviceToken: String,
) {
    fun fetchConfig(): WorkerConfig {
        val body = request(
            method = "GET",
            path = "/api/v1/devices/${deviceId.urlEncode()}/config",
            payload = null,
            timeoutMs = 15_000,
        )
        return WorkerConfig.fromJson(JSONObject(body))
    }

    fun nextTask(timeoutSeconds: Int): JSONObject? {
        val body = request(
            method = "GET",
            path = "/api/v1/devices/${deviceId.urlEncode()}/tasks/next",
            payload = null,
            timeoutMs = timeoutSeconds * 1_000,
        )
        if (body.isBlank()) return null
        val json = JSONObject(body)
        return if (json.optString("id").isBlank()) null else json
    }

    fun sendResult(taskId: String, result: JSONObject, timeoutSeconds: Int) {
        request(
            method = "POST",
            path = "/api/v1/devices/${deviceId.urlEncode()}/tasks/${taskId.urlEncode()}/result",
            payload = result,
            timeoutMs = timeoutSeconds * 1_000,
        )
    }

    private fun request(method: String, path: String, payload: JSONObject?, timeoutMs: Int): String {
        val connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("X-Device-Id", deviceId)
        if (deviceToken.isNotBlank()) {
            connection.setRequestProperty("X-Device-Token", deviceToken)
        }

        if (payload != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
            }
        }

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use(BufferedReader::readText).orEmpty()
        connection.disconnect()

        if (code !in 200..299) error("HTTP $code: $text")
        return text
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")
}