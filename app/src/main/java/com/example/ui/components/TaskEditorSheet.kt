package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DailyTask
import com.example.data.model.TaskCategory
import com.example.data.model.TaskUrgency
import com.example.data.model.TimeWindow
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorSheet(
    editingTask: DailyTask?,
    onDismiss: () -> Unit,
    onSave: (
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
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today = remember { LocalDate.now().toEpochDay() }

    var title by remember(editingTask) { mutableStateOf(editingTask?.title ?: "") }
    var notes by remember(editingTask) { mutableStateOf(editingTask?.notes ?: "") }
    var category by remember(editingTask) {
        mutableStateOf(editingTask?.categoryEnum() ?: TaskCategory.TRABAJO)
    }
    var urgency by remember(editingTask) {
        mutableStateOf(editingTask?.urgencyEnum() ?: TaskUrgency.MEDIA)
    }
    var timeWindow by remember(editingTask) {
        mutableStateOf(editingTask?.timeWindowEnum() ?: TimeWindow.MANANA)
    }
    var estimatedMinutes by remember(editingTask) {
        mutableLongStateOf(editingTask?.estimatedMinutes ?: 30L)
    }
    var dueDateEpochDay by remember(editingTask) {
        mutableLongStateOf(editingTask?.dueDateEpochDay ?: today)
    }
    var dailyRecurring by remember(editingTask) {
        mutableStateOf(editingTask?.dailyRecurring ?: false)
    }
    var reminderEnabled by remember(editingTask) {
        mutableStateOf(editingTask?.reminderEnabled ?: false)
    }
    var reminderHour by remember(editingTask) {
        mutableLongStateOf(editingTask?.reminderHour ?: 9L)
    }
    var reminderMinute by remember(editingTask) {
        mutableLongStateOf(editingTask?.reminderMinute ?: 0L)
    }
    var showTimePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (editingTask == null) "Nueva tarea inteligente" else "Editar tarea",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 140) title = it },
                label = { Text("¿Qué necesitas lograr?") },
                placeholder = { Text("Ej. Cerrar propuesta de diseño") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_title_input")
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { if (it.length <= 600) notes = it },
                label = { Text("Notas o contexto (opcional)") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_notes_input")
            )

            SectionLabel("Categoría")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat.label) }
                    )
                }
            }

            SectionLabel("Urgencia base")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskUrgency.entries.forEach { level ->
                    FilterChip(
                        selected = urgency == level,
                        onClick = { urgency = level },
                        label = { Text(level.label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            SectionLabel("Bloque ideal del día")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeWindow.entries.forEach { window ->
                    FilterChip(
                        selected = timeWindow == window,
                        onClick = { timeWindow = window },
                        label = { Text(window.label) }
                    )
                }
            }

            SectionLabel("¿Para cuándo?")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Hoy" to today,
                    "Mañana" to today + 1,
                    "En 2 días" to today + 2
                ).forEach { (label, epoch) ->
                    FilterChip(
                        selected = dueDateEpochDay == epoch,
                        onClick = { dueDateEpochDay = epoch },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            SectionLabel("Duración estimada")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15L, 30L, 45L, 60L, 90L).forEach { mins ->
                    FilterChip(
                        selected = estimatedMinutes == mins,
                        onClick = { estimatedMinutes = mins },
                        label = { Text("$mins min") }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Repeat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text("Rutina diaria", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Se reactiva cada mañana automáticamente",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = dailyRecurring,
                    onCheckedChange = { dailyRecurring = it },
                    modifier = Modifier.testTag("task_recurring_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text("Recordatorio puntual", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (reminderEnabled) {
                                "Alerta a las %02d:%02d".format(reminderHour, reminderMinute)
                            } else {
                                "Sin notificación individual"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (reminderEnabled) {
                        OutlinedButton(onClick = { showTimePicker = true }) {
                            Text("%02d:%02d".format(reminderHour, reminderMinute))
                        }
                    }
                    Switch(
                        checked = reminderEnabled,
                        onCheckedChange = { enabled ->
                            reminderEnabled = enabled
                            if (enabled) showTimePicker = true
                        },
                        modifier = Modifier.testTag("task_reminder_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text("Cancelar")
                }
                Button(
                    onClick = {
                        onSave(
                            title.trim(),
                            notes.trim().takeIf { it.isNotEmpty() },
                            category,
                            urgency,
                            timeWindow,
                            estimatedMinutes,
                            dueDateEpochDay,
                            dailyRecurring,
                            reminderEnabled,
                            reminderHour,
                            reminderMinute
                        )
                    },
                    enabled = title.trim().isNotEmpty(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1.4f)
                        .height(50.dp)
                        .testTag("save_task_button")
                ) {
                    Text(
                        text = if (editingTask == null) "Guardar tarea" else "Actualizar",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = reminderHour.toInt(),
            initialMinute = reminderMinute.toInt(),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        reminderHour = pickerState.hour.toLong()
                        reminderMinute = pickerState.minute.toLong()
                        showTimePicker = false
                    }
                ) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancelar")
                }
            },
            title = { Text("Hora del recordatorio") },
            text = { TimePicker(state = pickerState) }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
