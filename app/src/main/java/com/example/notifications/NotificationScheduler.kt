package com.example.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.model.DailyTask
import com.example.data.model.UserPreferences
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object NotificationScheduler {

    private const val MORNING_WORK = "pulso_morning_reminder"
    private const val EVENING_WORK = "pulso_evening_reminder"

    fun syncSchedule(
        context: Context,
        prefs: UserPreferences,
        tasks: List<DailyTask>
    ) {
        val pending = tasks.filter { !it.completed }
        NotificationHelper.cacheStateForWorker(context, prefs, pending)

        val workManager = WorkManager.getInstance(context)
        if (prefs.remindersEnabled) {
            scheduleDailySlot(
                context = context,
                isEvening = false,
                hour = prefs.morningHour.toInt(),
                minute = prefs.morningMinute.toInt(),
                forceTomorrow = false
            )
            if (prefs.eveningEnabled) {
                scheduleDailySlot(
                    context = context,
                    isEvening = true,
                    hour = prefs.eveningHour.toInt(),
                    minute = prefs.eveningMinute.toInt(),
                    forceTomorrow = false
                )
            } else {
                workManager.cancelUniqueWork(EVENING_WORK)
            }
        } else {
            workManager.cancelUniqueWork(MORNING_WORK)
            workManager.cancelUniqueWork(EVENING_WORK)
        }
    }

    fun scheduleDailySlot(
        context: Context,
        isEvening: Boolean,
        hour: Int,
        minute: Int,
        forceTomorrow: Boolean
    ) {
        val now = LocalDateTime.now()
        var target = now.withHour(hour.coerceIn(0, 23))
            .withMinute(minute.coerceIn(0, 59))
            .withSecond(0)
            .withNano(0)
        if (forceTomorrow || !target.isAfter(now)) {
            target = target.plusDays(1)
        }
        val delayMillis = Duration.between(now, target).toMillis().coerceAtLeast(1000L)

        val input = Data.Builder()
            .putString(
                DailyReminderWorker.KEY_KIND,
                if (isEvening) DailyReminderWorker.KIND_EVENING else DailyReminderWorker.KIND_MORNING
            )
            .putInt(DailyReminderWorker.KEY_HOUR, hour)
            .putInt(DailyReminderWorker.KEY_MINUTE, minute)
            .build()

        val request = OneTimeWorkRequestBuilder<DailyReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(input)
            .build()

        val workName = if (isEvening) EVENING_WORK else MORNING_WORK
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun scheduleTaskReminder(
        context: Context,
        taskId: String,
        title: String,
        dueDateEpochDay: Long,
        enabled: Boolean,
        hour: Int,
        minute: Int
    ) {
        val workName = "pulso_task_$taskId"
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(workName)
            return
        }

        val now = LocalDateTime.now()
        val targetDate = LocalDate.ofEpochDay(dueDateEpochDay)
        val targetDateTime = LocalDateTime.of(
            targetDate,
            LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        )
        if (!targetDateTime.isAfter(now)) {
            return
        }

        val delayMillis = Duration.between(now, targetDateTime).toMillis().coerceAtLeast(1000L)
        val input = Data.Builder()
            .putString(DailyReminderWorker.KEY_KIND, DailyReminderWorker.KIND_TASK)
            .putString(DailyReminderWorker.KEY_TASK_TITLE, title)
            .putInt(DailyReminderWorker.KEY_NOTIFICATION_ID, taskId.hashCode())
            .build()

        val request = OneTimeWorkRequestBuilder<DailyReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(input)
            .build()

        workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request)
    }
}
