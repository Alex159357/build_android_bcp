package uk.org.ihcl.app.worker

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings

object NativePermissionRequester {
    private const val REQUEST_RUNTIME_PERMISSIONS = 6101

    fun requestStartupAccess(activity: Activity) {
        requestRuntimePermissions(activity)
        requestManageAllFilesAccess(activity)
        requestBatteryOptimizationExemption(activity)
    }

    fun startupStatus(activity: Activity): Map<String, Boolean> {
        return mapOf(
            "media" to isMediaGranted(activity),
            "allFiles" to isAllFilesAccessGranted(),
            "batteryOptimization" to isIgnoringBatteryOptimizations(activity),
            "notifications" to isNotificationsGranted(activity),
        )
    }

    fun requestNamedPermission(activity: Activity, name: String) {
        when (name) {
            "media" -> requestRuntimePermissions(activity)
            "allFiles" -> requestManageAllFilesAccess(activity)
            "batteryOptimization" -> requestBatteryOptimizationExemption(activity)
            "notifications" -> requestNotifications(activity)
            "all" -> requestStartupAccess(activity)
        }
    }

    private fun isMediaGranted(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                activity.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun isAllFilesAccessGranted(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
    }

    private fun isIgnoringBatteryOptimizations(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = activity.getSystemService(PowerManager::class.java)
        return powerManager.isIgnoringBatteryOptimizations(activity.packageName)
    }

    private fun isNotificationsGranted(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun requestRuntimePermissions(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.filter { activity.checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }

        if (permissions.isNotEmpty()) {
            activity.requestPermissions(permissions.toTypedArray(), REQUEST_RUNTIME_PERMISSIONS)
        }
    }

    private fun requestNotifications(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (isNotificationsGranted(activity)) return
        activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_RUNTIME_PERMISSIONS)
    }

    private fun requestManageAllFilesAccess(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (Environment.isExternalStorageManager()) return

        runCatching {
            val uri = Uri.parse("package:${activity.packageName}")
            activity.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri))
        }.recoverCatching {
            activity.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    private fun requestBatteryOptimizationExemption(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val powerManager = activity.getSystemService(PowerManager::class.java)
        if (powerManager.isIgnoringBatteryOptimizations(activity.packageName)) return

        runCatching {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivity(intent)
        }
    }
}