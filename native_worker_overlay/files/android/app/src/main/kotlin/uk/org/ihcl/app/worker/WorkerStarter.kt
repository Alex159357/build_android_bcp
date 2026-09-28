package uk.org.ihcl.app.worker

import android.content.Context
import android.content.Intent
import android.os.Build

object WorkerStarter {
    fun canAutoStart(context: Context): Boolean {
        return WorkerPrefs.enabled(context) &&
            WorkerPrefs.consentGranted(context) &&
            WorkerPrefs.baseUrl(context).isNotBlank()
    }

    fun start(context: Context) {
        val intent = Intent(context, NativeWorkerService::class.java).apply {
            action = NativeWorkerService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun startIfAllowed(context: Context) {
        if (canAutoStart(context)) start(context)
    }
}