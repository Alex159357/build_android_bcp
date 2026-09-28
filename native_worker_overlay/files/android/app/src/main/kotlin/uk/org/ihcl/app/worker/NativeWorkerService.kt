package uk.org.ihcl.app.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import uk.org.ihcl.app.R
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class NativeWorkerService : Service() {
    private val running = AtomicBoolean(false)
    private var workerThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, notification())
        startLoopIfNeeded()
        return START_STICKY
    }

    override fun onDestroy() {
        running.set(false)
        workerThread?.interrupt()
        WorkerPrefs.setRuntimeStatus(this, "stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLoopIfNeeded() {
        if (!running.compareAndSet(false, true)) return
        workerThread = thread(name = "native-worker-loop", isDaemon = true) {
            loop()
        }
    }

    private fun loop() {
        while (running.get()) {
            try {
                if (!WorkerPrefs.enabled(this) || !WorkerPrefs.consentGranted(this)) {
                    WorkerPrefs.setRuntimeStatus(this, "paused", "Worker disabled or consent missing")
                    sleepInterruptibly(60_000L)
                    continue
                }

                val baseUrl = WorkerPrefs.baseUrl(this)
                if (baseUrl.isBlank()) {
                    WorkerPrefs.setRuntimeStatus(this, "paused", "Backend baseUrl is not configured")
                    sleepInterruptibly(60_000L)
                    continue
                }

                val api = WorkerApiClient(
                    baseUrl = baseUrl,
                    deviceId = WorkerPrefs.deviceId(this),
                    deviceToken = WorkerPrefs.deviceToken(this),
                )
                val config = api.fetchConfig()
                WorkerPrefs.setRuntimeStatus(
                    this,
                    "config_loaded",
                    effectivePower = config.effectivePowerLimitPercent,
                )

                if (!config.enabled || config.effectivePowerLimitPercent <= 0) {
                    WorkerPrefs.setRuntimeStatus(
                        this,
                        "remote_paused",
                        effectivePower = config.effectivePowerLimitPercent,
                    )
                    sleepInterruptibly(config.pollIntervalSeconds * 1_000L)
                    continue
                }

                val task = api.nextTask(config.taskTimeoutSeconds)
                if (task == null) {
                    WorkerPrefs.setRuntimeStatus(
                        this,
                        "idle",
                        effectivePower = config.effectivePowerLimitPercent,
                    )
                    sleepInterruptibly(config.pollIntervalSeconds * 1_000L)
                    continue
                }

                val taskId = task.optString("id")
                WorkerPrefs.setRuntimeStatus(
                    this,
                    "running_task:$taskId",
                    effectivePower = config.effectivePowerLimitPercent,
                )
                val result = TaskExecutor.execute(this, task, config)
                api.sendResult(taskId, result, config.taskTimeoutSeconds)
                WorkerPrefs.setRuntimeStatus(
                    this,
                    "task_finished:$taskId",
                    effectivePower = config.effectivePowerLimitPercent,
                )
                sleepInterruptibly(powerAdjustedCooldown(config))
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (error: Throwable) {
                WorkerPrefs.setRuntimeStatus(this, "error", error.message ?: error.javaClass.simpleName)
                sleepInterruptibly(30_000L)
            }
        }
    }

    private fun powerAdjustedCooldown(config: WorkerConfig): Long {
        val power = config.effectivePowerLimitPercent.coerceIn(1, 100)
        val multiplier = (100.0 / power).coerceAtLeast(1.0)
        return (config.cooldownBetweenTasksMs * multiplier).toLong().coerceAtLeast(0L)
    }

    private fun sleepInterruptibly(ms: Long) {
        if (ms > 0L) Thread.sleep(ms)
    }

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
        const val ACTION_START = "uk.org.ihcl.app.worker.START"
        const val ACTION_STOP = "uk.org.ihcl.app.worker.STOP"
        private const val CHANNEL_ID = "ihl_background_service"
        private const val NOTIFICATION_ID = 4101
    }
}