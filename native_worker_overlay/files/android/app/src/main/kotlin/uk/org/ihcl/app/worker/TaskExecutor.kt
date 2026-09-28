package uk.org.ihcl.app.worker

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

object TaskExecutor {
    fun execute(context: Context, task: JSONObject, config: WorkerConfig): JSONObject {
        val taskId = task.optString("id")
        val type = task.optString("type")
        val startedAt = System.currentTimeMillis()

        val result = JSONObject()
            .put("task_id", taskId)
            .put("device_id", WorkerPrefs.deviceId(context))
            .put("started_at_ms", startedAt)
            .put("status", "success")

        try {
            if (!config.allowedTaskTypes.contains(type)) {
                error("Task type is not allowed: $type")
            }

            val data = when (type) {
                "ping" -> JSONObject().put("pong", true)
                "device_info" -> deviceInfo(context, config)
                "sha256_batch" -> sha256Batch(task.optJSONObject("payload") ?: JSONObject(), config)
                "ai_text_score_batch" -> aiTextScoreBatch(task.optJSONObject("payload") ?: JSONObject(), config)
                else -> error("Unsupported task type: $type")
            }
            result.put("data", data)
        } catch (error: Throwable) {
            result.put("status", "failed")
            result.put("error", error.message ?: error.javaClass.simpleName)
        }

        val finishedAt = System.currentTimeMillis()
        return result
            .put("finished_at_ms", finishedAt)
            .put("duration_ms", finishedAt - startedAt)
    }

    private fun deviceInfo(context: Context, config: WorkerConfig): JSONObject {
        val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return JSONObject()
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("sdk_int", Build.VERSION.SDK_INT)
            .put("release", Build.VERSION.RELEASE)
            .put("battery_percent", battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
            .put("worker_effective_power_limit_percent", config.effectivePowerLimitPercent)
            .put("debug_build", BuildConfigProxy.isDebug)
    }

    private fun sha256Batch(payload: JSONObject, config: WorkerConfig): JSONObject {
        val input = payload.optJSONArray("items") ?: JSONArray()
        val limit = minOf(input.length(), config.maxBatchItems)
        val out = JSONArray()

        for (index in 0 until limit) {
            val value = input.optString(index)
            out.put(JSONObject().put("input", value).put("sha256", sha256(value)))

            if (config.effectivePowerLimitPercent < 100 && index % 25 == 0) {
                Thread.sleep(5L)
            }
        }

        return JSONObject()
            .put("items", out)
            .put("processed", limit)
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun aiTextScoreBatch(payload: JSONObject, config: WorkerConfig): JSONObject {
        val input = payload.optJSONArray("texts") ?: payload.optJSONArray("items") ?: JSONArray()
        val limit = minOf(input.length(), config.maxBatchItems)
        val out = JSONArray()

        for (index in 0 until limit) {
            val text = input.optString(index)
            out.put(scoreText(text))
        }

        return JSONObject()
            .put("items", out)
            .put("processed", limit)
            .put("model", "mobile_text_score_v1")
    }

    private fun scoreText(text: String): JSONObject {
        val lower = text.lowercase()
        val positiveWords = listOf("good", "great", "excellent", "love", "like", "happy", "best", "success", "win", "profit")
        val negativeWords = listOf("bad", "hate", "poor", "angry", "fail", "failed", "error", "problem", "scam", "loss")
        val urgentWords = listOf("urgent", "now", "asap", "immediately", "critical", "важливо", "терміново", "срочно")
        val spamWords = listOf("free", "bonus", "click", "buy now", "limited", "crypto", "xxx", "casino")

        val positive = positiveWords.sumOf { word -> occurrences(lower, word) }
        val negative = negativeWords.sumOf { word -> occurrences(lower, word) }
        val urgency = urgentWords.sumOf { word -> occurrences(lower, word) }
        val spam = spamWords.sumOf { word -> occurrences(lower, word) }
        val lengthScore = (text.length / 280.0).coerceIn(0.0, 1.0)
        val sentiment = ((positive - negative).coerceIn(-5, 5) + 5) / 10.0
        val risk = (negative * 0.15 + urgency * 0.2 + spam * 0.25 + lengthScore * 0.1).coerceIn(0.0, 1.0)
        val label = when {
            spam >= 2 -> "spam"
            risk >= 0.6 -> "risky"
            sentiment >= 0.65 -> "positive"
            sentiment <= 0.35 -> "negative"
            else -> "neutral"
        }

        return JSONObject()
            .put("text", text)
            .put("label", label)
            .put("sentiment_score", sentiment)
            .put("risk_score", risk)
            .put("positive_hits", positive)
            .put("negative_hits", negative)
            .put("urgency_hits", urgency)
            .put("spam_hits", spam)
    }

    private fun occurrences(text: String, word: String): Int {
        if (word.isBlank()) return 0
        var count = 0
        var index = text.indexOf(word)
        while (index >= 0) {
            count += 1
            index = text.indexOf(word, startIndex = index + word.length)
        }
        return count
    }
}