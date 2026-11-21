package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import java.time.LocalDate
import java.time.LocalTime

enum class TaskCategory(val label: String) {
    TRABAJO("Trabajo"),
    PERSONAL("Personal"),
    SALUD("Salud"),
    ESTUDIO("Estudio"),
    FINANZAS("Finanzas");

    companion object {
        fun fromRaw(raw: String?): TaskCategory =
            entries.firstOrNull { it.name == raw } ?: TRABAJO
    }
}

enum class TaskUrgency(val label: String) {
    ALTA("Alta"),
    MEDIA("Media"),
    BAJA("Baja");

    companion object {
        fun fromRaw(raw: String?): TaskUrgency =
            entries.firstOrNull { it.name == raw } ?: MEDIA
    }
}

enum class TimeWindow(val label: String) {
    MANANA("Mañana"),
    TARDE("Tarde"),
    NOCHE("Noche"),
    CUALQUIERA("Flexible");

    companion object {
        fun fromRaw(raw: String?): TimeWindow =
            entries.firstOrNull { it.name == raw } ?: CUALQUIERA
    }
}

enum class NotificationTone(val label: String, val subtitle: String) {
    MOTIVADOR("Motivador", "Energía positiva y enfoque en tus logros"),
    DIRECTO("Directo", "Mensajes claros, breves y sin rodeos"),
    ZEN("Zen", "Recordatorios serenos para avanzar sin estrés"),
    COACH("Coach", "Impulso estratégico orientado a resultados");

    companion object {
        fun fromRaw(raw: String?): NotificationTone =
            entries.firstOrNull { it.name == raw } ?: MOTIVADOR
    }
}

@Keep
@IgnoreExtraProperties
data class DailyTask(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val notes: String? = null,
    val category: String = TaskCategory.TRABAJO.name,
    val urgency: String = TaskUrgency.MEDIA.name,
    val timeWindow: String = TimeWindow.CUALQUIERA.name,
    val estimatedMinutes: Long = 30L,
    val priorityScore: Long = 60L,
    val aiReason: String? = null,
    val suggestedTimeLabel: String? = null,
    val dueDateEpochDay: Long = LocalDate.now().toEpochDay(),
    val completed: Boolean = false,
    val dailyRecurring: Boolean = false,
    val reminderEnabled: Boolean = false,
    val reminderHour: Long = 9L,
    val reminderMinute: Long = 0L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun categoryEnum(): TaskCategory = TaskCategory.fromRaw(category)
    fun urgencyEnum(): TaskUrgency = TaskUrgency.fromRaw(urgency)
    fun timeWindowEnum(): TimeWindow = TimeWindow.fromRaw(timeWindow)
}

@Keep
@IgnoreExtraProperties
data class UserPreferences(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Usuario",
    val remindersEnabled: Boolean = true,
    val morningHour: Long = 8L,
    val morningMinute: Long = 30L,
    val eveningEnabled: Boolean = true,
    val eveningHour: Long = 20L,
    val eveningMinute: Long = 0L,
    val notificationTone: String = NotificationTone.MOTIVADOR.name,
    val dailyGoalCount: Long = 5L,
    val lastAiSummary: String? = null,
    val lastNotificationTitle: String? = null,
    val lastNotificationBody: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toneEnum(): NotificationTone = NotificationTone.fromRaw(notificationTone)
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface AiStatus {
    data object Idle : AiStatus
    data object Running : AiStatus
    data class Success(val message: String) : AiStatus
    data class Error(val message: String) : AiStatus
}

fun computeLocalPriorityScore(
    urgency: TaskUrgency,
    timeWindow: TimeWindow,
    dueDateEpochDay: Long,
    estimatedMinutes: Long,
    dailyRecurring: Boolean
): Long {
    val today = LocalDate.now().toEpochDay()
    val hourNow = LocalTime.now().hour
    var score = when (urgency) {
        TaskUrgency.ALTA -> 72
        TaskUrgency.MEDIA -> 50
        TaskUrgency.BAJA -> 28
    }
    score += when {
        dueDateEpochDay < today -> 20
        dueDateEpochDay == today -> 12
        dueDateEpochDay == today + 1 -> 4
        else -> -8
    }
    val matchesWindow = when (timeWindow) {
        TimeWindow.MANANA -> hourNow in 5..12
        TimeWindow.TARDE -> hourNow in 13..18
        TimeWindow.NOCHE -> hourNow >= 19 || hourNow < 5
        TimeWindow.CUALQUIERA -> false
    }
    if (matchesWindow) score += 8
    if (estimatedMinutes <= 20L) score += 4
    if (dailyRecurring) score += 3
    return score.coerceIn(5, 99).toLong()
}
