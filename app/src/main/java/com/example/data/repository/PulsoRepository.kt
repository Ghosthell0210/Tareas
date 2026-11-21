package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.DailyTask
import com.example.data.model.NotificationTone
import com.example.data.model.TaskCategory
import com.example.data.model.TaskUrgency
import com.example.data.model.TimeWindow
import com.example.data.model.UserPreferences
import com.example.data.model.computeLocalPriorityScore
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

data class AiTaskPriorityUpdate(
    val taskId: String,
    val priorityScore: Long,
    val aiReason: String,
    val suggestedTimeLabel: String
)

class PulsoRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Debes iniciar sesión con Google para acceder a tus tareas.")
    }

    fun observeTasks(userId: String): Flow<List<DailyTask>> = flow {
        require(userId.isNotBlank()) { "userId cannot be blank" }
        val path = "users/$userId/tasks"
        emitAll(
            db.collection("users").document(userId).collection("tasks")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        DailyTask::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observePreferences(userId: String): Flow<UserPreferences?> = flow {
        require(userId.isNotBlank()) { "userId cannot be blank" }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserPreferences::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserPreferences(userId: String, displayName: String): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "La sesión activa cambió; operación cancelada." }
            val docRef = db.collection("users").document(uid)
            val existingSnap = docRef.get().await()
            if (!existingSnap.exists()) {
                val safeName = displayName.trim().ifBlank { "Usuario" }.take(80)
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "remindersEnabled" to true,
                    "morningHour" to 8L,
                    "morningMinute" to 30L,
                    "eveningEnabled" to true,
                    "eveningHour" to 20L,
                    "eveningMinute" to 0L,
                    "notificationTone" to NotificationTone.MOTIVADOR.name,
                    "dailyGoalCount" to 5L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addTask(
        userId: String,
        title: String,
        notes: String?,
        category: TaskCategory,
        urgency: TaskUrgency,
        timeWindow: TimeWindow,
        estimatedMinutes: Long,
        dueDateEpochDay: Long,
        dailyRecurring: Boolean,
        reminderEnabled: Boolean,
        reminderHour: Long,
        reminderMinute: Long
    ): Result<String> {
        var docPath = "users/$userId/tasks"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "La sesión activa cambió; operación cancelada." }
            val cleanTitle = title.trim().take(140)
            require(cleanTitle.isNotEmpty()) { "El título de la tarea no puede estar vacío." }

            val cleanMinutes = estimatedMinutes.coerceIn(5L, 480L)
            val cleanEpochDay = dueDateEpochDay.coerceIn(0L, 100000L)
            val cleanHour = reminderHour.coerceIn(0L, 23L)
            val cleanMinute = reminderMinute.coerceIn(0L, 59L)
            val initialScore = computeLocalPriorityScore(
                urgency = urgency,
                timeWindow = timeWindow,
                dueDateEpochDay = cleanEpochDay,
                estimatedMinutes = cleanMinutes,
                dailyRecurring = dailyRecurring
            )

            val docRef = db.collection("users").document(uid).collection("tasks").document()
            docPath = docRef.path
            val cleanNotes = notes?.trim()?.take(600)?.takeIf { it.isNotEmpty() }

            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "notes" to cleanNotes,
                "category" to category.name,
                "urgency" to urgency.name,
                "timeWindow" to timeWindow.name,
                "estimatedMinutes" to cleanMinutes,
                "priorityScore" to initialScore,
                "dueDateEpochDay" to cleanEpochDay,
                "completed" to false,
                "dailyRecurring" to dailyRecurring,
                "reminderEnabled" to reminderEnabled,
                "reminderHour" to cleanHour,
                "reminderMinute" to cleanMinute,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateTask(
        userId: String,
        task: DailyTask,
        title: String,
        notes: String?,
        category: TaskCategory,
        urgency: TaskUrgency,
        timeWindow: TimeWindow,
        estimatedMinutes: Long,
        dueDateEpochDay: Long,
        dailyRecurring: Boolean,
        reminderEnabled: Boolean,
        reminderHour: Long,
        reminderMinute: Long
    ): Result<Unit> {
        val docPath = "users/$userId/tasks/${task.id}"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "La sesión activa cambió; operación cancelada." }
            require(task.id.isNotBlank()) { "ID de tarea inválido." }
            val cleanTitle = title.trim().take(140)
            require(cleanTitle.isNotEmpty()) { "El título de la tarea no puede estar vacío." }

            val cleanMinutes = estimatedMinutes.coerceIn(5L, 480L)
            val cleanEpochDay = dueDateEpochDay.coerceIn(0L, 100000L)
            val newScore = computeLocalPriorityScore(
                urgency = urgency,
                timeWindow = timeWindow,
                dueDateEpochDay = cleanEpochDay,
                estimatedMinutes = cleanMinutes,
                dailyRecurring = dailyRecurring
            )

            val docRef = db.collection("users").document(uid).collection("tasks").document(task.id)
            val payload = mapOf(
                "title" to cleanTitle,
                "notes" to (notes?.trim()?.take(600) ?: ""),
                "category" to category.name,
                "urgency" to urgency.name,
                "timeWindow" to timeWindow.name,
                "estimatedMinutes" to cleanMinutes,
                "priorityScore" to newScore,
                "dueDateEpochDay" to cleanEpochDay,
                "dailyRecurring" to dailyRecurring,
                "reminderEnabled" to reminderEnabled,
                "reminderHour" to reminderHour.coerceIn(0L, 23L),
                "reminderMinute" to reminderMinute.coerceIn(0L, 59L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleTaskCompletion(userId: String, task: DailyTask): Result<Unit> {
        val docPath = "users/$userId/tasks/${task.id}"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "La sesión activa cambió; operación cancelada." }
            require(task.id.isNotBlank()) { "ID de tarea inválido." }
            val docRef = db.collection("users").document(uid).collection("tasks").document(task.id)
            docRef.update(
                mapOf(
                    "completed" to !task.completed,
                    "dueDateEpochDay" to task.dueDateEpochDay,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun rollOverRecurringTasks(userId: String, tasks: List<DailyTask>): Result<Unit> {
        val today = LocalDate.now().toEpochDay()
        val toReset = tasks.filter { it.dailyRecurring && it.dueDateEpochDay < today }
        if (toReset.isEmpty()) return Result.success(Unit)
        val path = "users/$userId/tasks"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "La sesión activa cambió; operación cancelada." }
            val batch = db.batch()
            toReset.forEach { task ->
                if (task.id.isNotBlank()) {
                    val ref = db.collection("users").document(uid).collection("tasks").document(task.id)
                    batch.update(
                        ref,
                        mapOf(
                            "completed" to false,
                            "dueDateEpochDay" to today,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun applyAiPriorities(
        userId: String,
        updates: List<AiTaskPriorityUpdate>
    ): Result<Unit> {
        if (updates.isEmpty()) return Result.success(Unit)
        val path = "users/$userId/tasks"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "La sesión activa cambió; operación cancelada." }
            val batch = db.batch()
            updates.forEach { item ->
                if (item.taskId.isNotBlank()) {
                    val ref = db.collection("users").document(uid)
                        .collection("tasks").document(item.taskId)
                    batch.update(
                        ref,
                        mapOf(
                            "priorityScore" to item.priorityScore.coerceIn(0L, 100L),
                            "aiReason" to item.aiReason.trim().take(240),
                            "suggestedTimeLabel" to item.suggestedTimeLabel.trim().take(20),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteTask(userId: String, taskId: String): Result<Unit> {
        val docPath = "users/$userId/tasks/$taskId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "La sesión activa cambió; operación cancelada." }
            require(taskId.isNotBlank()) { "ID de tarea inválido." }
            db.collection("users").document(uid).collection("tasks").document(taskId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateReminderSettings(
        userId: String,
        displayName: String,
        remindersEnabled: Boolean,
        morningHour: Long,
        morningMinute: Long,
        eveningEnabled: Boolean,
        eveningHour: Long,
        eveningMinute: Long,
        tone: NotificationTone,
        dailyGoalCount: Long
    ): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "La sesión activa cambió; operación cancelada." }
            val docRef = db.collection("users").document(uid)
            docRef.update(
                mapOf(
                    "displayName" to displayName.trim().ifBlank { "Usuario" }.take(80),
                    "remindersEnabled" to remindersEnabled,
                    "morningHour" to morningHour.coerceIn(0L, 23L),
                    "morningMinute" to morningMinute.coerceIn(0L, 59L),
                    "eveningEnabled" to eveningEnabled,
                    "eveningHour" to eveningHour.coerceIn(0L, 23L),
                    "eveningMinute" to eveningMinute.coerceIn(0L, 59L),
                    "notificationTone" to tone.name,
                    "dailyGoalCount" to dailyGoalCount.coerceIn(1L, 30L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateAiInsight(
        userId: String,
        summary: String,
        notificationTitle: String,
        notificationBody: String
    ): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "La sesión activa cambió; operación cancelada." }
            db.collection("users").document(uid).update(
                mapOf(
                    "lastAiSummary" to summary.trim().take(500),
                    "lastNotificationTitle" to notificationTitle.trim().take(120),
                    "lastNotificationBody" to notificationBody.trim().take(300),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }
}
