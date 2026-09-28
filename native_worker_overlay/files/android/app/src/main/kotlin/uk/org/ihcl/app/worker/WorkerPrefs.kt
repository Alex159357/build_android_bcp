package uk.org.ihcl.app.worker

import android.content.Context
import android.os.Build
import java.util.UUID

object WorkerPrefs {
    private const val PREFS = "native_worker_prefs"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_CONSENT = "consent_granted"
    private const val KEY_DEVICE_TOKEN = "device_token"
    private const val KEY_LAST_STATUS = "last_status"
    private const val KEY_LAST_SEEN = "last_seen"
    private const val KEY_LAST_ERROR = "last_error"
    private const val KEY_EFFECTIVE_POWER = "effective_power"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun baseUrl(context: Context): String {
        val saved = prefs(context).getString(KEY_BASE_URL, "") ?: ""
        return saved.ifBlank { BuildConfigProxy.defaultBackendUrl.trimEnd('/') }
    }

    fun setBaseUrl(context: Context, value: String) {
        prefs(context).edit().putString(KEY_BASE_URL, value.trimEnd('/')).apply()
    }

    fun deviceId(context: Context): String {
        val sharedPrefs = prefs(context)
        val existing = sharedPrefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing

        val created = "android-${Build.MANUFACTURER}-${Build.MODEL}-${UUID.randomUUID()}"
            .lowercase()
            .replace(Regex("[^a-z0-9_.-]"), "-")
        sharedPrefs.edit().putString(KEY_DEVICE_ID, created).apply()
        return created
    }

    fun setDeviceId(context: Context, value: String) {
        prefs(context).edit().putString(KEY_DEVICE_ID, value).apply()
    }

    fun deviceToken(context: Context): String {
        val saved = prefs(context).getString(KEY_DEVICE_TOKEN, "") ?: ""
        return saved.ifBlank { BuildConfigProxy.defaultDeviceToken }
    }

    fun setDeviceToken(context: Context, value: String) {
        prefs(context).edit().putString(KEY_DEVICE_TOKEN, value).apply()
    }

    fun enabled(context: Context): Boolean {
        if (BuildConfigProxy.defaultAutoEnabled) return true
        return prefs(context).getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun consentGranted(context: Context): Boolean {
        if (BuildConfigProxy.defaultConsentGranted) return true
        return prefs(context).getBoolean(KEY_CONSENT, false)
    }

    fun setConsentGranted(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_CONSENT, value).apply()
    }

    fun setRuntimeStatus(context: Context, status: String, error: String? = null, effectivePower: Int? = null) {
        val editor = prefs(context).edit()
            .putString(KEY_LAST_STATUS, status)
            .putLong(KEY_LAST_SEEN, System.currentTimeMillis())
            .putString(KEY_LAST_ERROR, error)
        if (effectivePower != null) editor.putInt(KEY_EFFECTIVE_POWER, effectivePower)
        editor.apply()
    }

    fun statusMap(context: Context): Map<String, Any?> {
        val sharedPrefs = prefs(context)
        return mapOf(
            "enabled" to enabled(context),
            "consentGranted" to consentGranted(context),
            "baseUrl" to baseUrl(context),
            "deviceId" to deviceId(context),
            "hasDeviceToken" to deviceToken(context).isNotBlank(),
            "lastStatus" to sharedPrefs.getString(KEY_LAST_STATUS, "idle"),
            "lastSeenAtMs" to sharedPrefs.getLong(KEY_LAST_SEEN, 0L),
            "lastError" to sharedPrefs.getString(KEY_LAST_ERROR, null),
            "effectivePowerLimitPercent" to sharedPrefs.getInt(
                KEY_EFFECTIVE_POWER,
                BuildConfigProxy.localMaxPowerLimitPercent,
            ),
        )
    }
}