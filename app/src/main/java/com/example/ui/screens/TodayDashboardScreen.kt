package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AiStatus
import com.example.data.model.DailyTask
import com.example.data.model.UiState
import com.example.data.model.UserPreferences
import com.example.ui.components.DailyTaskCard
import com.example.ui.theme.CoralPulse
import com.example.ui.theme.ElectricMintDark
import com.example.ui.theme.MidnightSlate
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class TodayFilter(val label: String) {
    PENDIENTES("Pendientes hoy"),
    ALTA_PRIORIDAD("Alta prioridad"),
    COMPLETADAS("Completadas")
}

@Composable
fun TodayDashboardScreen(
    tasksState: UiState<List<DailyTask>>,
    preferencesState: UiState<UserPreferences>,
    aiStatus: AiStatus,
    onRunAiPrioritization: () -> Unit,
    onToggleComplete: (DailyTask) -> Unit,
    onEditTask: (DailyTask) -> Unit,
    onDeleteTask: (DailyTask) -> Unit,
    onAddNewTask: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf(TodayFilter.PENDIENTES) }
    val prefs = (preferencesState as? UiState.Success)?.data
    val todayEpoch = remember { LocalDate.now().toEpochDay() }
    val greeting = remember {
        when (LocalTime.now().hour) {
            in 5..11 -> "Buenos días"
            in 12..19 -> "Buenas tardes"
            else -> "Buenas noches"
        }
    }
    val dateHeader = remember {
        LocalDate.now()
            .format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "ES")))
            .replaceFirstChar { it.uppercase() }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            HeroCommandCard(
                greeting = greeting,
                userName = prefs?.displayName?.substringBefore(" ") ?: "Productivo",
                dateHeader = dateHeader,
                tasksState = tasksState,
                dailyGoal = (prefs?.dailyGoalCount ?: 5L).toInt(),
                todayEpoch = todayEpoch,
                aiStatus = aiStatus,
                onRunAiPrioritization = onRunAiPrioritization
            )
        }

        if (aiStatus is AiStatus.Error) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = aiStatus.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        val aiSummary = prefs?.lastAiSummary
        if (!aiSummary.isNullOrBlank()) {
            item {
                AnimatedVisibility(visible = true) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = "Resumen IA",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Estrategia IA de hoy",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = aiSummary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(TodayFilter.entries) { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.label) },
                        modifier = Modifier.testTag("filter_${filter.name.lowercase()}")
                    )
                }
            }
        }

        when (tasksState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            is UiState.Error -> {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = tasksState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            is UiState.Success -> {
                val filtered = when (selectedFilter) {
                    TodayFilter.PENDIENTES ->
                        tasksState.data.filter { !it.completed && it.dueDateEpochDay <= todayEpoch }
                    TodayFilter.ALTA_PRIORIDAD ->
                        tasksState.data.filter { !it.completed && it.priorityScore >= 70L }
                    TodayFilter.COMPLETADAS ->
                        tasksState.data.filter { it.completed }
                }

                if (filtered.isEmpty()) {
                    item {
                        EmptyTodayCard(
                            selectedFilter = selectedFilter,
                            onAddNewTask = onAddNewTask
                        )
                    }
                } else {
                    items(filtered, key = { it.id }) { task ->
                        DailyTaskCard(
                            task = task,
                            onToggleComplete = { onToggleComplete(task) },
                            onEdit = { onEditTask(task) },
                            onDelete = { onDeleteTask(task) }
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(76.dp))
        }
    }
}

@Composable
private fun HeroCommandCard(
    greeting: String,
    userName: String,
    dateHeader: String,
    tasksState: UiState<List<DailyTask>>,
    dailyGoal: Int,
    todayEpoch: Long,
    aiStatus: AiStatus,
    onRunAiPrioritization: () -> Unit
) {
    val allTasks = (tasksState as? UiState.Success)?.data.orEmpty()
    val completedCount = allTasks.count { it.completed }
    val pendingTodayCount = allTasks.count { !it.completed && it.dueDateEpochDay <= todayEpoch }
    val progress = if (dailyGoal > 0) (completedCount.toFloat() / dailyGoal).coerceIn(0f, 1f) else 0f

    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightSlate),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_hero_pulse),
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(26.dp)),
                contentScale = ContentScale.Crop,
                alpha = 0.55f
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MidnightSlate.copy(alpha = 0.92f),
                                MidnightSlate.copy(alpha = 0.65f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = dateHeader,
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricMintDark
                    )
                    Text(
                        text = "$greeting, $userName",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Text(
                        text = if (pendingTodayCount == 0) {
                            "Tu bandeja de hoy está despejada."
                        } else {
                            "Tienes $pendingTodayCount tareas activas para hoy."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFCBD5E1)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Meta diaria",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFE2E8F0)
                        )
                        Text(
                            text = "$completedCount / $dailyGoal completadas",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricMintDark
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        color = ElectricMintDark,
                        trackColor = Color.White.copy(alpha = 0.16f),
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                    )
                }

                Button(
                    onClick = onRunAiPrioritization,
                    enabled = aiStatus !is AiStatus.Running && allTasks.any { !it.completed },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CoralPulse,
                        contentColor = Color.White,
                        disabledContainerColor = CoralPulse.copy(alpha = 0.45f),
                        disabledContentColor = Color.White.copy(alpha = 0.75f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("ai_prioritize_button")
                ) {
                    if (aiStatus is AiStatus.Running) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analizando prioridades con IA...", fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Priorizar mi día con IA", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTodayCard(
    selectedFilter: TodayFilter,
    onAddNewTask: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_tasks),
                contentDescription = "Sin tareas pendientes",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
            Text(
                text = when (selectedFilter) {
                    TodayFilter.PENDIENTES -> "Todo bajo control por hoy"
                    TodayFilter.ALTA_PRIORIDAD -> "Sin urgencias críticas ahora"
                    TodayFilter.COMPLETADAS -> "Aún no completas tareas hoy"
                },
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Agrega una nueva tarea y deja que Pulso calcule su prioridad óptima y te avise a tiempo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onAddNewTask,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("empty_add_task_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Crear tarea")
            }
        }
    }
}
