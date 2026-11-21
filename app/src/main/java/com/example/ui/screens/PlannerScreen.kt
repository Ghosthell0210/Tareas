package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DailyTask
import com.example.data.model.TaskCategory
import com.example.data.model.UiState
import com.example.ui.components.DailyTaskCard
import java.time.LocalDate

@Composable
fun PlannerScreen(
    tasksState: UiState<List<DailyTask>>,
    onToggleComplete: (DailyTask) -> Unit,
    onEditTask: (DailyTask) -> Unit,
    onDeleteTask: (DailyTask) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf<TaskCategory?>(null) }
    val todayEpoch = remember { LocalDate.now().toEpochDay() }
    val allTasks = (tasksState as? UiState.Success)?.data.orEmpty()
    val filtered = if (selectedCategory == null) {
        allTasks
    } else {
        allTasks.filter { it.categoryEnum() == selectedCategory }
    }

    val pendingToday = filtered.filter { !it.completed && it.dueDateEpochDay <= todayEpoch }
    val upcoming = filtered.filter { !it.completed && it.dueDateEpochDay > todayEpoch }
    val routines = filtered.filter { it.dailyRecurring }
    val totalMinutesPending = filtered.filter { !it.completed }.sumOf { it.estimatedMinutes }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Organizador inteligente",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Visualiza tu carga por bloques, categorías y rutinas diarias.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryStatCard(
                    label = "Pendientes",
                    value = "${filtered.count { !it.completed }}",
                    modifier = Modifier.weight(1f)
                )
                SummaryStatCard(
                    label = "Carga estimada",
                    value = "${totalMinutesPending}m",
                    modifier = Modifier.weight(1f)
                )
                SummaryStatCard(
                    label = "Rutinas",
                    value = "${routines.size}",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("Todas") }
                    )
                }
                items(TaskCategory.entries) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat.label) }
                    )
                }
            }
        }

        if (pendingToday.isNotEmpty()) {
            item { GroupHeader("Para hoy (${pendingToday.size})") }
            items(pendingToday, key = { "today_${it.id}" }) { task ->
                DailyTaskCard(
                    task = task,
                    onToggleComplete = { onToggleComplete(task) },
                    onEdit = { onEditTask(task) },
                    onDelete = { onDeleteTask(task) }
                )
            }
        }

        if (upcoming.isNotEmpty()) {
            item { GroupHeader("Próximos días (${upcoming.size})") }
            items(upcoming, key = { "upcoming_${it.id}" }) { task ->
                DailyTaskCard(
                    task = task,
                    onToggleComplete = { onToggleComplete(task) },
                    onEdit = { onEditTask(task) },
                    onDelete = { onDeleteTask(task) }
                )
            }
        }

        val completed = filtered.filter { it.completed }
        if (completed.isNotEmpty()) {
            item { GroupHeader("Completadas (${completed.size})") }
            items(completed, key = { "done_${it.id}" }) { task ->
                DailyTaskCard(
                    task = task,
                    onToggleComplete = { onToggleComplete(task) },
                    onEdit = { onEditTask(task) },
                    onDelete = { onDeleteTask(task) }
                )
            }
        }

        if (filtered.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No hay tareas en esta categoría todavía.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(76.dp)) }
    }
}

@Composable
private fun SummaryStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 4.dp)
    )
}
