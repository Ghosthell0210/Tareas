package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.DailyTask
import com.example.data.model.NotificationTone
import com.example.data.model.UserPreferences

object NotificationHelper {
    const val CHANNEL_ID = "pulso_daily_reminders"
    private const val PREFS_NAME = "pulso_reminder_cache"
    private const val KEY_ENABLED = "reminders_enabled"
    private const val KEY_EVENING_ENABLED = "evening_enabled"
    private const val KEY_TONE = "tone"
    private const val KEY_TOP_TASK = "top_task"
    private const val KEY_PENDING_COUNT = "pending_count"
    private const val KEY_CUSTOM_TITLE = "custom_title"
    private const val KEY_CUSTOM_BODY = "custom_body"
    private const val KEY_USER_NAME = "user_name"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notification_channel_desc)
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun cacheStateForWorker(
        context: Context,
        prefs: UserPreferences,
        pendingTasks: List<DailyTask>
    ) {
        val topTask = pendingTasks.maxByOrNull { it.priorityScore }?.title
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, prefs.remindersEnabled)
            .putBoolean(KEY_EVENING_ENABLED, prefs.eveningEnabled)
            .putString(KEY_TONE, prefs.notificationTone)
            .putString(KEY_USER_NAME, prefs.displayName)
            .putString(KEY_TOP_TASK, topTask)
            .putInt(KEY_PENDING_COUNT, pendingTasks.size)
            .putString(KEY_CUSTOM_TITLE, prefs.lastNotificationTitle)
            .putString(KEY_CUSTOM_BODY, prefs.lastNotificationBody)
            .apply()
    }

    fun showPersonalizedNotification(
        context: Context,
        notificationId: Int,
        titleOverride: String? = null,
        bodyOverride: String? = null,
        isEvening: Boolean = false
    ): Boolean {
        if (!hasNotificationPermission(context)) return false
        ensureChannel(context)

        val cache = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val userName = cache.getString(KEY_USER_NAME, "hola")?.substringBefore(" ") ?: "hola"
        val tone = NotificationTone.fromRaw(cache.getString(KEY_TONE, NotificationTone.MOTIVADOR.name))
        val topTask = cache.getString(KEY_TOP_TASK, null)
        val pendingCount = cache.getInt(KEY_PENDING_COUNT, 0)
        val cachedTitle = cache.getString(KEY_CUSTOM_TITLE, null)
        val cachedBody = cache.getString(KEY_CUSTOM_BODY, null)

        val (title, body) = when {
            !titleOverride.isNullOrBlank() && !bodyOverride.isNullOrBlank() ->
                titleOverride to bodyOverride
            !isEvening && !cachedTitle.isNullOrBlank() && !cachedBody.isNullOrBlank() ->
                cachedTitle to cachedBody
            else -> buildContextualCopy(userName, tone, topTask, pendingCount, isEvening)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
        return true
    }

    fun isDailyEnabled(context: Context, isEvening: Boolean): Boolean {
        val cache = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val master = cache.getBoolean(KEY_ENABLED, true)
        return if (isEvening) master && cache.getBoolean(KEY_EVENING_ENABLED, true) else master
    }

    private fun buildContextualCopy(
        userName: String,
        tone: NotificationTone,
        topTask: String?,
        pendingCount: Int,
        isEvening: Boolean
    ): Pair<String, String> {
        if (isEvening) {
            return when (tone) {
                NotificationTone.MOTIVADOR ->
                    "Cierre del día, $userName" to if (pendingCount == 0) {
                        "Día completado al 100%. Prepara mañana con calma."
                    } else {
                        "Te quedan $pendingCount tareas. Cierra una más o reprograma con IA."
                    }
                NotificationTone.DIRECTO ->
                    "Resumen nocturno ($pendingCount pendientes)" to (topTask?.let { "Prioridad pendiente: $it" } ?: "Todo al día.")
                NotificationTone.ZEN ->
                    "Respira y revisa tu avance" to "Tómate 2 minutos para cerrar tu jornada con claridad."
                NotificationTone.COACH ->
                    "Check-in de cierre, $userName" to (topTask?.let { "Remata '$it' o muévela al bloque matutino." } ?: "Meta cumplida hoy.")
            }
        }

        val taskLabel = topTask ?: "Define tu prioridad #1 de hoy"
        return when (tone) {
            NotificationTone.MOTIVADOR ->
                "¡Buenos días, $userName! Tu foco #1" to "Arranca con \"$taskLabel\" (${pendingCount.coerceAtLeast(1)} en agenda)."
            NotificationTone.DIRECTO ->
                "Prioridad #1 hoy" to "$taskLabel ($pendingCount tareas activas)."
            NotificationTone.ZEN ->
                "Un paso claro para hoy" to "Empieza con serenidad en: $taskLabel."
            NotificationTone.COACH ->
                "Tu bloque de alto impacto" to "Completa \"$taskLabel\" primero y gana el día."
        }
    }
}
