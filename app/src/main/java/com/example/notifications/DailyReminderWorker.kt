package com.example.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DailyReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val kind = inputData.getString(KEY_KIND) ?: KIND_MORNING
        val hour = inputData.getInt(KEY_HOUR, 8)
        val minute = inputData.getInt(KEY_MINUTE, 30)

        return when (kind) {
            KIND_TASK -> {
                val taskTitle = inputData.getString(KEY_TASK_TITLE) ?: "Tarea prioritaria"
                val taskIdHash = inputData.getInt(KEY_NOTIFICATION_ID, 2001)
                NotificationHelper.showPersonalizedNotification(
                    context = applicationContext,
                    notificationId = taskIdHash,
                    titleOverride = "Recordatorio de tarea",
                    bodyOverride = "Es momento de avanzar con: $taskTitle"
                )
                Result.success()
            }
            KIND_EVENING -> {
                if (NotificationHelper.isDailyEnabled(applicationContext, isEvening = true)) {
                    NotificationHelper.showPersonalizedNotification(
                        context = applicationContext,
                        notificationId = 1002,
                        isEvening = true
                    )
                    NotificationScheduler.scheduleDailySlot(
                        context = applicationContext,
                        isEvening = true,
                        hour = hour,
                        minute = minute,
                        forceTomorrow = true
                    )
                }
                Result.success()
            }
            else -> {
                if (NotificationHelper.isDailyEnabled(applicationContext, isEvening = false)) {
                    NotificationHelper.showPersonalizedNotification(
                        context = applicationContext,
                        notificationId = 1001,
                        isEvening = false
                    )
                    NotificationScheduler.scheduleDailySlot(
                        context = applicationContext,
                        isEvening = false,
                        hour = hour,
                        minute = minute,
                        forceTomorrow = true
                    )
                }
                Result.success()
            }
        }
    }

    companion object {
        const val KEY_KIND = "kind"
        const val KEY_HOUR = "hour"
        const val KEY_MINUTE = "minute"
        const val KEY_TASK_TITLE = "task_title"
        const val KEY_NOTIFICATION_ID = "notification_id"

        const val KIND_MORNING = "morning"
        const val KIND_EVENING = "evening"
        const val KIND_TASK = "task"
    }
}
