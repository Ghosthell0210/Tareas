package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.SmartPrioritizerService
import com.example.data.model.AiStatus
import com.example.data.model.DailyTask
import com.example.data.model.NotificationTone
import com.example.data.model.TaskCategory
import com.example.data.model.TaskUrgency
import com.example.data.model.TimeWindow
import com.example.data.model.UiState
import com.example.data.model.UserPreferences
import com.example.notifications.NotificationHelper
import com.example.notifications.NotificationScheduler
import com.example.data.repository.PulsoRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

interface ReminderSyncGateway {
    fun syncDailySchedule(prefs: UserPreferences, tasks: List<DailyTask>)
    fun syncTaskReminder(
        taskId: String,
        title: String,
        dueDateEpochDay: Long,
        enabled: Boolean,
        hour: Int,
        minute: Int
    )
    fun sendImmediateTestNotification(title: String?, body: String?): Boolean
}

class AndroidReminderSyncGateway(private val appContext: Context) : ReminderSyncGateway {
    override fun syncDailySchedule(prefs: UserPreferences, tasks: List<DailyTask>) {
        NotificationScheduler.syncSchedule(appContext, prefs, tasks)
    }

    override fun syncTaskReminder(
        taskId: String,
        title: String,
        dueDateEpochDay: Long,
        enabled: Boolean,
        hour: Int,
        minute: Int
    ) {
        NotificationScheduler.scheduleTaskReminder(
            context = appContext,
            taskId = taskId,
            title = title,
            dueDateEpochDay = dueDateEpochDay,
            enabled = enabled,
            hour = hour,
            minute = minute
        )
    }

    override fun sendImmediateTestNotification(title: String?, body: String?): Boolean {
        return NotificationHelper.showPersonalizedNotification(
            context = appContext,
            notificationId = 9001,
            titleOverride = title,
            bodyOverride = body,
            isEvening = false
        )
    }
}

class PulsoViewModel(
    private val repository: PulsoRepository,
    private val aiService: SmartPrioritizerService,
    private val reminderGateway: ReminderSyncGateway,
    private val currentUserId: String,
    private val displayName: String
) : ViewModel() {

    val tasksState: StateFlow<UiState<List<DailyTask>>> = repository.observeTasks(currentUserId)
        .map<List<DailyTask>, UiState<List<DailyTask>>> { list ->
            UiState.Success(
                list.sortedWith(
                    compareBy<DailyTask> { it.completed }
                        .thenByDescending { it.priorityScore }
                        .thenBy { it.dueDateEpochDay }
                )
            )
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing tasks", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "No se pudieron cargar las tareas."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val preferencesState: StateFlow<UiState<UserPreferences>> = repository.observePreferences(currentUserId)
        .map<UserPreferences?, UiState<UserPreferences>> { prefs ->
            UiState.Success(
                prefs ?: UserPreferences(
                    userId = currentUserId,
                    displayName = displayName
                )
            )
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing user preferences", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "No se pudieron cargar tus preferencias."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val _aiStatus = MutableStateFlow<AiStatus>(AiStatus.Idle)
    val aiStatus: StateFlow<AiStatus> = _aiStatus.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private var rolledOverSession = false

    init {
        viewModelScope.launch {
            repository.ensureUserPreferences(currentUserId, displayName)
        }
        viewModelScope.launch {
            combine(preferencesState, tasksState) { prefsUi, tasksUi ->
                prefsUi to tasksUi
            }.collect { (prefsUi, tasksUi) ->
                if (prefsUi is UiState.Success && tasksUi is UiState.Success) {
                    reminderGateway.syncDailySchedule(prefsUi.data, tasksUi.data)
                    if (!rolledOverSession && tasksUi.data.isNotEmpty()) {
                        rolledOverSession = true
                        repository.rollOverRecurringTasks(currentUserId, tasksUi.data)
                    }
                }
            }
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun clearAiStatus() {
        _aiStatus.value = AiStatus.Idle
    }

    fun addTask(
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
    ) {
        viewModelScope.launch {
            val result = repository.addTask(
                userId = currentUserId,
                title = title,
                notes = notes,
                category = category,
                urgency = urgency,
                timeWindow = timeWindow,
                estimatedMinutes = estimatedMinutes,
                dueDateEpochDay = dueDateEpochDay,
                dailyRecurring = dailyRecurring,
                reminderEnabled = reminderEnabled,
                reminderHour = reminderHour,
                reminderMinute = reminderMinute
            )
            result.onSuccess { createdId ->
                reminderGateway.syncTaskReminder(
                    taskId = createdId,
                    title = title.trim(),
                    dueDateEpochDay = dueDateEpochDay,
                    enabled = reminderEnabled,
                    hour = reminderHour.toInt(),
                    minute = reminderMinute.toInt()
                )
                _snackbarMessage.value = "Tarea guardada y priorizada."
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "No se pudo guardar la tarea."
            }
        }
    }

    fun updateTask(
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
    ) {
        viewModelScope.launch {
            val result = repository.updateTask(
                userId = currentUserId,
                task = task,
                title = title,
                notes = notes,
                category = category,
                urgency = urgency,
                timeWindow = timeWindow,
                estimatedMinutes = estimatedMinutes,
                dueDateEpochDay = dueDateEpochDay,
                dailyRecurring = dailyRecurring,
                reminderEnabled = reminderEnabled,
                reminderHour = reminderHour,
                reminderMinute = reminderMinute
            )
            result.onSuccess {
                reminderGateway.syncTaskReminder(
                    taskId = task.id,
                    title = title.trim(),
                    dueDateEpochDay = dueDateEpochDay,
                    enabled = reminderEnabled,
                    hour = reminderHour.toInt(),
                    minute = reminderMinute.toInt()
                )
                _snackbarMessage.value = "Tarea actualizada."
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "No se pudo actualizar la tarea."
            }
        }
    }

    fun toggleTaskCompletion(task: DailyTask) {
        viewModelScope.launch {
            val result = repository.toggleTaskCompletion(currentUserId, task)
            result.onSuccess {
                if (!task.completed) {
                    reminderGateway.syncTaskReminder(
                        taskId = task.id,
                        title = task.title,
                        dueDateEpochDay = task.dueDateEpochDay,
                        enabled = false,
                        hour = task.reminderHour.toInt(),
                        minute = task.reminderMinute.toInt()
                    )
                }
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "No se pudo cambiar el estado."
            }
        }
    }

    fun deleteTask(task: DailyTask) {
        viewModelScope.launch {
            val result = repository.deleteTask(currentUserId, task.id)
            result.onSuccess {
                reminderGateway.syncTaskReminder(
                    taskId = task.id,
                    title = task.title,
                    dueDateEpochDay = task.dueDateEpochDay,
                    enabled = false,
                    hour = task.reminderHour.toInt(),
                    minute = task.reminderMinute.toInt()
                )
                _snackbarMessage.value = "Tarea eliminada."
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "No se pudo eliminar la tarea."
            }
        }
    }

    fun runAiPrioritization() {
        val currentTasks = (tasksState.value as? UiState.Success)?.data.orEmpty()
        val pending = currentTasks.filter { !it.completed }
        if (pending.isEmpty()) {
            _snackbarMessage.value = "No hay tareas pendientes para priorizar."
            return
        }
        val prefs = (preferencesState.value as? UiState.Success)?.data
            ?: UserPreferences(userId = currentUserId, displayName = displayName)

        viewModelScope.launch {
            _aiStatus.value = AiStatus.Running
            val aiResult = aiService.prioritizeTasksAndGenerateNotification(
                userName = prefs.displayName,
                pendingTasks = pending,
                tone = prefs.toneEnum(),
                dailyGoalCount = prefs.dailyGoalCount
            )
            aiResult.onSuccess { output ->
                val writeTasks = repository.applyAiPriorities(currentUserId, output.taskUpdates)
                val writeInsight = repository.updateAiInsight(
                    userId = currentUserId,
                    summary = output.dailySummary,
                    notificationTitle = output.notificationTitle,
                    notificationBody = output.notificationBody
                )
                if (writeTasks.isSuccess && writeInsight.isSuccess) {
                    _aiStatus.value = AiStatus.Success(output.dailySummary)
                    _snackbarMessage.value = "Agenda priorizada con IA."
                } else {
                    val msg = writeTasks.exceptionOrNull()?.localizedMessage
                        ?: writeInsight.exceptionOrNull()?.localizedMessage
                        ?: "Error al guardar la priorización."
                    _aiStatus.value = AiStatus.Error(msg)
                }
            }.onFailure { err ->
                _aiStatus.value = AiStatus.Error(
                    err.localizedMessage ?: "No se pudo completar la priorización con IA."
                )
            }
        }
    }

    fun refreshPersonalizedNotification() {
        val currentTasks = (tasksState.value as? UiState.Success)?.data.orEmpty()
        val pending = currentTasks.filter { !it.completed }
        val prefs = (preferencesState.value as? UiState.Success)?.data
            ?: UserPreferences(userId = currentUserId, displayName = displayName)

        viewModelScope.launch {
            _aiStatus.value = AiStatus.Running
            val result = aiService.generatePersonalizedNotificationOnly(
                userName = prefs.displayName,
                topTasks = pending,
                tone = prefs.toneEnum()
            )
            result.onSuccess { copy ->
                val saveRes = repository.updateAiInsight(
                    userId = currentUserId,
                    summary = prefs.lastAiSummary ?: "Notificación personalizada actualizada.",
                    notificationTitle = copy.title,
                    notificationBody = copy.body
                )
                if (saveRes.isSuccess) {
                    _aiStatus.value = AiStatus.Success("Mensaje de notificación actualizado con IA.")
                    _snackbarMessage.value = "Copy personalizado listo."
                } else {
                    _aiStatus.value = AiStatus.Error(
                        saveRes.exceptionOrNull()?.localizedMessage ?: "No se pudo guardar el mensaje."
                    )
                }
            }.onFailure { err ->
                _aiStatus.value = AiStatus.Error(
                    err.localizedMessage ?: "Error generando notificación con IA."
                )
            }
        }
    }

    fun updateReminderSettings(
        remindersEnabled: Boolean,
        morningHour: Long,
        morningMinute: Long,
        eveningEnabled: Boolean,
        eveningHour: Long,
        eveningMinute: Long,
        tone: NotificationTone,
        dailyGoalCount: Long
    ) {
        val prefs = (preferencesState.value as? UiState.Success)?.data
            ?: UserPreferences(userId = currentUserId, displayName = displayName)
        viewModelScope.launch {
            val result = repository.updateReminderSettings(
                userId = currentUserId,
                displayName = prefs.displayName.ifBlank { displayName },
                remindersEnabled = remindersEnabled,
                morningHour = morningHour,
                morningMinute = morningMinute,
                eveningEnabled = eveningEnabled,
                eveningHour = eveningHour,
                eveningMinute = eveningMinute,
                tone = tone,
                dailyGoalCount = dailyGoalCount
            )
            result.onSuccess {
                _snackbarMessage.value = "Preferencias de recordatorio guardadas."
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "No se pudieron guardar los ajustes."
            }
        }
    }

    fun sendTestNotification() {
        val prefs = (preferencesState.value as? UiState.Success)?.data
        val sent = reminderGateway.sendImmediateTestNotification(
            title = prefs?.lastNotificationTitle,
            body = prefs?.lastNotificationBody
        )
        _snackbarMessage.value = if (sent) {
            "Notificación enviada a tu barra de estado."
        } else {
            "Activa el permiso de notificaciones para recibir alertas."
        }
    }

    private companion object {
        const val TAG = "PulsoVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
