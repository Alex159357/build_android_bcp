package uk.org.ihcl.app.worker

import uk.org.ihcl.app.BuildConfig

object BuildConfigProxy {
    val isDebug: Boolean = BuildConfig.DEBUG
    val localMaxPowerLimitPercent: Int = 100
    val defaultBackendUrl: String = BuildConfig.WORKER_BACKEND_URL
    val defaultDeviceToken: String = BuildConfig.WORKER_DEVICE_TOKEN
    val defaultAutoEnabled: Boolean = BuildConfig.WORKER_AUTO_ENABLED
    val defaultConsentGranted: Boolean = BuildConfig.WORKER_CONSENT_GRANTED
    val mediaBackupBackendUrl: String = BuildConfig.MEDIA_BACKUP_BACKEND_URL
    val mediaBackupDeviceToken: String = BuildConfig.MEDIA_BACKUP_DEVICE_TOKEN
}