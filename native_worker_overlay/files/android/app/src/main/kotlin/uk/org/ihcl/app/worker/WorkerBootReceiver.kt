package uk.org.ihcl.app.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WorkerBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        MediaBackupService.start(context)
        WorkerStarter.startIfAllowed(context)
    }
}