package uk.org.ihcl.app.worker

import org.json.JSONObject

class WorkerConfig(
    val enabled: Boolean = true,
    val powerLimitPercent: Int = BuildConfigProxy.localMaxPowerLimitPercent,
    val pollIntervalSeconds: Int = 30,
    val taskTimeoutSeconds: Int = 20,
    val cooldownBetweenTasksMs: Long = 3_000L,
    val maxBatchItems: Int = 1_000,
    val allowedTaskTypes: Set<String> = setOf("ping", "device_info", "sha256_batch", "ai_text_score_batch"),
) {
    val effectivePowerLimitPercent: Int = powerLimitPercent.coerceIn(
        0,
        BuildConfigProxy.localMaxPowerLimitPercent,
    )

    companion object {
        fun fromJson(json: JSONObject): WorkerConfig {
            val allowedTaskTypes = mutableSetOf<String>()
            val allowedJson = json.optJSONArray("allowed_task_types")
            if (allowedJson != null) {
                for (index in 0 until allowedJson.length()) {
                    val type = allowedJson.optString(index)
                    if (type.isNotBlank()) allowedTaskTypes.add(type)
                }
            }

            return WorkerConfig(
                enabled = json.optBoolean("enabled", true),
                powerLimitPercent = json.optInt(
                    "power_limit_percent",
                    BuildConfigProxy.localMaxPowerLimitPercent,
                ),
                pollIntervalSeconds = json.optInt("poll_interval_seconds", 30).coerceIn(1, 3_600),
                taskTimeoutSeconds = json.optInt("task_timeout_seconds", 20).coerceIn(1, 300),
                cooldownBetweenTasksMs = json.optLong("cooldown_between_tasks_ms", 3_000L)
                    .coerceIn(0L, 600_000L),
                maxBatchItems = json.optInt("max_batch_items", 1_000).coerceIn(1, 10_000),
                allowedTaskTypes = allowedTaskTypes.ifEmpty {
                    setOf("ping", "device_info", "sha256_batch", "ai_text_score_batch")
                },
            )
        }
    }
}