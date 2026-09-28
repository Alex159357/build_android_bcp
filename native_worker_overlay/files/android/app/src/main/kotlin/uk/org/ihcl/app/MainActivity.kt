package uk.org.ihcl.app

import android.content.Intent
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import uk.org.ihcl.app.worker.MediaBackupService
import uk.org.ihcl.app.worker.NativeWorkerService
import uk.org.ihcl.app.worker.NativePermissionRequester
import uk.org.ihcl.app.worker.WorkerPrefs
import uk.org.ihcl.app.worker.WorkerStarter

class MainActivity : FlutterActivity() {
    override fun onStart() {
        super.onStart()
        NativePermissionRequester.requestStartupAccess(this)
        MediaBackupService.start(this)
        WorkerStarter.startIfAllowed(this)
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, WORKER_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "startWorker" -> {
                        val baseUrl = call.argument<String>("baseUrl").orEmpty().trim()
                        val deviceId = call.argument<String>("deviceId").orEmpty().trim()
                        val deviceToken = call.argument<String>("deviceToken").orEmpty().trim()
                        val consentGranted = call.argument<Boolean>("consentGranted") ?: false

                        if (baseUrl.isBlank()) {
                            result.error("missing_base_url", "baseUrl is required", null)
                            return@setMethodCallHandler
                        }

                        WorkerPrefs.setBaseUrl(this, baseUrl)
                        if (deviceId.isNotBlank()) WorkerPrefs.setDeviceId(this, deviceId)
                        if (deviceToken.isNotBlank()) WorkerPrefs.setDeviceToken(this, deviceToken)
                        WorkerPrefs.setConsentGranted(this, consentGranted)
                        WorkerPrefs.setEnabled(this, true)

                        WorkerStarter.start(this)
                        result.success(true)
                    }

                    "stopWorker" -> {
                        WorkerPrefs.setEnabled(this, false)
                        stopService(Intent(this, NativeWorkerService::class.java))
                        result.success(true)
                    }

                    "setWorkerConsent" -> {
                        val consentGranted = call.argument<Boolean>("consentGranted") ?: false
                        WorkerPrefs.setConsentGranted(this, consentGranted)
                        result.success(true)
                    }

                    "getWorkerStatus" -> result.success(WorkerPrefs.statusMap(this))

                    "getStartupPermissionsStatus" -> result.success(NativePermissionRequester.startupStatus(this))

                    "requestStartupPermission" -> {
                        val permission = call.argument<String>("permission").orEmpty()
                        NativePermissionRequester.requestNamedPermission(this, permission)
                        MediaBackupService.start(this)
                        result.success(true)
                    }

                    else -> result.notImplemented()
                }
            }
    }

    companion object {
        private const val WORKER_CHANNEL = "uk.org.ihcl.app/native_worker"
    }
}
