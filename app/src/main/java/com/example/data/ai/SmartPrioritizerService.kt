package com.example.data.ai

import android.util.Log
import com.example.data.model.DailyTask
import com.example.data.model.NotificationTone
import com.example.data.repository.AiTaskPriorityUpdate
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val TAG = "SmartPrioritizer"

data class AiPrioritizationResult(
    val dailySummary: String,
    val notificationTitle: String,
    val notificationBody: String,
    val taskUpdates: List<AiTaskPriorityUpdate>
)

data class PersonalizedNotificationCopy(
    val title: String,
    val body: String
)

class SmartPrioritizerService {

    private val prioritizationSchema = Schema.obj(
        properties = mapOf(
            "dailySummary" to Schema.string(
                description = "Resumen estratégico en español de 1 a 2 frases sobre cómo abordar el día."
            ),
            "notificationTitle" to Schema.string(
                description = "Título corto (máx 65 caracteres) y personalizado para notificación push."
            ),
            "notificationBody" to Schema.string(
                description = "Cuerpo breve (máx 150 caracteres) motivador y específico citando la tarea #1."
            ),
            "rankedTasks" to Schema.array(
                description = "Lista de todas las tareas activas evaluadas con su puntuación de prioridad.",
                items = Schema.obj(
                    properties = mapOf(
                        "taskId" to Schema.string(description = "ID exacto de la tarea"),
                        "priorityScore" to Schema.integer(description = "Puntuación de 1 a 100"),
                        "aiReason" to Schema.string(description = "Razón breve (máx 90 caracteres) en español"),
                        "suggestedTimeLabel" to Schema.string(description = "Hora sugerida formato HH:mm, ej. 09:30")
                    )
                )
            )
        )
    )

    private val notificationOnlySchema = Schema.obj(
        properties = mapOf(
            "notificationTitle" to Schema.string(
                description = "Título corto y atractivo (máx 65 caracteres) para notificación push."
            ),
            "notificationBody" to Schema.string(
                description = "Mensaje personalizado (máx 150 caracteres) acorde al tono elegido."
            )
        )
    )

    private val prioritizationModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = prioritizationSchema
            }
        )
    }

    private val notificationModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = notificationOnlySchema
            }
        )
    }

    suspend fun prioritizeTasksAndGenerateNotification(
        userName: String,
        pendingTasks: List<DailyTask>,
        tone: NotificationTone,
        dailyGoalCount: Long
    ): Result<AiPrioritizationResult> {
        if (pendingTasks.isEmpty()) {
            return Result.failure(IllegalStateException("Agrega al menos una tarea pendiente para priorizar."))
        }
        return try {
            val today = LocalDate.now().toEpochDay()
            val timeNow = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            val tasksBlock = pendingTasks.joinToString(separator = "\n") { task ->
                val dayDelta = task.dueDateEpochDay - today
                val dueLabel = when {
                    dayDelta < 0 -> "atrasada ${-dayDelta} días"
                    dayDelta == 0L -> "hoy"
                    dayDelta == 1L -> "mañana"
                    else -> "en $dayDelta días"
                }
                "- id=${task.id} | titulo=\"${task.title}\" | categoria=${task.category} | urgencia=${task.urgency} | ventana=${task.timeWindow} | duracion=${task.estimatedMinutes}min | vence=$dueLabel | diaria=${task.dailyRecurring}"
            }

            val prompt = """
                Eres el motor inteligente de productividad de la app Pulso.
                Usuario: $userName
                Hora local actual: $timeNow
                Meta diaria: $dailyGoalCount tareas
                Tono de notificación deseado: ${tone.label} (${tone.subtitle})

                Evalúa las siguientes tareas pendientes, asigna un priorityScore (0-100) coherente con urgencia, fecha de vencimiento, duración (quick wins vs bloques profundos) y ventana horaria actual.
                Sugiere una hora óptima realista (suggestedTimeLabel en formato HH:mm) a partir de la hora actual o del día.
                Redacta aiReason en español claro y conciso.
                Además genera un título y cuerpo de notificación push personalizada para hoy mencionando la tarea prioritaria #1 en el tono solicitado.

                Tareas:
                $tasksBlock
            """.trimIndent()

            val response = withTimeout(60_000L) {
                prioritizationModel.generateContent(prompt)
            }
            val rawJson = response.text
                ?: throw IllegalStateException("El modelo devolvió una respuesta vacía.")

            val json = JSONObject(rawJson)
            val summary = json.optString("dailySummary", "Enfócate en tu bloque de mayor impacto primero.")
            val notifTitle = json.optString("notificationTitle", "Tu Pulso de hoy está listo")
            val notifBody = json.optString("notificationBody", "Empieza con tu tarea principal para tomar impulso.")
            val rankedArray = json.optJSONArray("rankedTasks")
            val validIds = pendingTasks.map { it.id }.toSet()
            val updates = mutableListOf<AiTaskPriorityUpdate>()

            if (rankedArray != null) {
                for (i in 0 until rankedArray.length()) {
                    val obj = rankedArray.optJSONObject(i) ?: continue
                    val id = obj.optString("taskId", "")
                    if (id in validIds) {
                        updates.add(
                            AiTaskPriorityUpdate(
                                taskId = id,
                                priorityScore = obj.optLong("priorityScore", 65L).coerceIn(1L, 100L),
                                aiReason = obj.optString("aiReason", "Prioridad ajustada automáticamente.").take(240),
                                suggestedTimeLabel = obj.optString("suggestedTimeLabel", "09:30").take(20)
                            )
                        )
                    }
                }
            }

            Result.success(
                AiPrioritizationResult(
                    dailySummary = summary.take(500),
                    notificationTitle = notifTitle.take(120),
                    notificationBody = notifBody.take(300),
                    taskUpdates = updates
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI prioritization timed out", e)
            Result.failure(Exception("La priorización con IA tardó más de 60 segundos. Intenta nuevamente."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI prioritization failed", e)
            Result.failure(e)
        }
    }

    suspend fun generatePersonalizedNotificationOnly(
        userName: String,
        topTasks: List<DailyTask>,
        tone: NotificationTone
    ): Result<PersonalizedNotificationCopy> {
        return try {
            val taskNames = if (topTasks.isEmpty()) {
                "Sin tareas pendientes aún; invitar a planificar 3 metas clave hoy."
            } else {
                topTasks.take(3).joinToString("; ") { "${it.title} (prioridad ${it.priorityScore})" }
            }
            val prompt = """
                Genera una notificación push diaria personalizada en español para $userName.
                Tono: ${tone.label} — ${tone.subtitle}.
                Principales tareas pendientes: $taskNames.
                Devuelve un título corto (máx 60 chars) y un cuerpo accionable (máx 140 chars).
            """.trimIndent()

            val response = withTimeout(60_000L) {
                notificationModel.generateContent(prompt)
            }
            val rawJson = response.text
                ?: throw IllegalStateException("El modelo devolvió una respuesta vacía.")
            val json = JSONObject(rawJson)
            Result.success(
                PersonalizedNotificationCopy(
                    title = json.optString("notificationTitle", "Tu momento de foco, $userName").take(120),
                    body = json.optString("notificationBody", "Revisa tu prioridad #1 en Pulso.").take(300)
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Notification copy generation timed out", e)
            Result.failure(Exception("La generación de notificación tardó demasiado. Intenta de nuevo."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Notification copy generation failed", e)
            Result.failure(e)
        }
    }
}
