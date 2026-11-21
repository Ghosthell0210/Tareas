package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AiStatus
import com.example.data.model.NotificationTone
import com.example.data.model.UiState
import com.example.data.model.UserPreferences
import com.example.notifications.NotificationHelper
import com.example.ui.theme.MidnightSlate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    preferencesState: UiState<UserPreferences>,
    aiStatus: AiStatus,
    userEmail: String?,
    onSaveSettings: (
        remindersEnabled: Boolean,
        morningHour: Long,
        morningMinute: Long,
        eveningEnabled: Boolean,
        eveningHour: Long,
        eveningMinute: Long,
        tone: NotificationTone,
        dailyGoalCount: Long
    ) -> Unit,
    onGenerateAiCopy: () -> Unit,
    onSendTestNotification: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = (preferencesState as? UiState.Success)?.data ?: UserPreferences()

    var hasPermission by remember {
        mutableStateOf(NotificationHelper.hasNotificationPermission(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    var pickingMorningTime by remember { mutableStateOf(false) }
    var pickingEveningTime by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Notificaciones inteligentes",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Configura tus recordatorios diarios y personaliza el tono de cada aviso push.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Permiso de notificaciones pendiente",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Actívalo para recibir tus alertas diarias personalizadas.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(
                            onClick = {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                            modifier = Modifier.testTag("grant_notifications_button")
                        ) {
                            Text("Activar")
                        }
                    }
                }
            }
        }

        // Schedule card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Recordatorio diario activo", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Resumen automático con tu prioridad #1",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = prefs.remindersEnabled,
                            onCheckedChange = { enabled ->
                                onSaveSettings(
                                    enabled,
                                    prefs.morningHour,
                                    prefs.morningMinute,
                                    prefs.eveningEnabled,
                                    prefs.eveningHour,
                                    prefs.eveningMinute,
                                    prefs.toneEnum(),
                                    prefs.dailyGoalCount
                                )
                            },
                            modifier = Modifier.testTag("master_reminders_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Aviso matutino", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Arranca el día con foco",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { pickingMorningTime = true },
                            enabled = prefs.remindersEnabled,
                            modifier = Modifier.testTag("morning_time_button")
                        ) {
                            Text("%02d:%02d".format(prefs.morningHour, prefs.morningMinute))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cierre nocturno", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Repaso de tareas pendientes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { pickingEveningTime = true },
                                enabled = prefs.remindersEnabled && prefs.eveningEnabled,
                                modifier = Modifier.testTag("evening_time_button")
                            ) {
                                Text("%02d:%02d".format(prefs.eveningHour, prefs.eveningMinute))
                            }
                            Switch(
                                checked = prefs.eveningEnabled,
                                enabled = prefs.remindersEnabled,
                                onCheckedChange = { evening ->
                                    onSaveSettings(
                                        prefs.remindersEnabled,
                                        prefs.morningHour,
                                        prefs.morningMinute,
                                        evening,
                                        prefs.eveningHour,
                                        prefs.eveningMinute,
                                        prefs.toneEnum(),
                                        prefs.dailyGoalCount
                                    )
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Meta diaria de tareas", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Objetivo de progreso en tu portada",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (prefs.dailyGoalCount > 1) {
                                        onSaveSettings(
                                            prefs.remindersEnabled,
                                            prefs.morningHour,
                                            prefs.morningMinute,
                                            prefs.eveningEnabled,
                                            prefs.eveningHour,
                                            prefs.eveningMinute,
                                            prefs.toneEnum(),
                                            prefs.dailyGoalCount - 1
                                        )
                                    }
                                }
                            ) {
                                Icon(Icons.Rounded.Remove, contentDescription = "Reducir meta")
                            }
                            Text(
                                text = "${prefs.dailyGoalCount}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = {
                                    if (prefs.dailyGoalCount < 20) {
                                        onSaveSettings(
                                            prefs.remindersEnabled,
                                            prefs.morningHour,
                                            prefs.morningMinute,
                                            prefs.eveningEnabled,
                                            prefs.eveningHour,
                                            prefs.eveningMinute,
                                            prefs.toneEnum(),
                                            prefs.dailyGoalCount + 1
                                        )
                                    }
                                }
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "Aumentar meta")
                            }
                        }
                    }
                }
            }
        }

        // Personalization tone selector
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Estilo de notificación", style = MaterialTheme.typography.titleMedium)
                    NotificationTone.entries.forEach { tone ->
                        val selected = prefs.toneEnum() == tone
                        Surface(
                            onClick = {
                                onSaveSettings(
                                    prefs.remindersEnabled,
                                    prefs.morningHour,
                                    prefs.morningMinute,
                                    prefs.eveningEnabled,
                                    prefs.eveningHour,
                                    prefs.eveningMinute,
                                    tone,
                                    prefs.dailyGoalCount
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            },
                            border = if (selected) {
                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            } else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tone_${tone.name.lowercase()}")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = tone.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = tone.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Personalized push preview + live test
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightSlate),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Vista previa de tu notificación push",
                            style = MaterialTheme.typography.titleSmall,
                            color = androidx.compose.ui.graphics.Color.White
                        )
                    }

                    Surface(
                        color = androidx.compose.ui.graphics.Color(0xFF1F2B44),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = prefs.lastNotificationTitle ?: "Pulso · Tu prioridad #1 de hoy",
                                style = MaterialTheme.typography.titleSmall,
                                color = androidx.compose.ui.graphics.Color.White
                            )
                            Text(
                                text = prefs.lastNotificationBody
                                    ?: "Toca 'Generar con IA' para redactar una alerta personalizada según tus tareas reales.",
                                style = MaterialTheme.typography.bodySmall,
                                color = androidx.compose.ui.graphics.Color(0xFFCBD5E1)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onGenerateAiCopy,
                            enabled = aiStatus !is AiStatus.Running,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("generate_ai_notif_button")
                        ) {
                            if (aiStatus is AiStatus.Running) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Redactar IA")
                        }
                        Button(
                            onClick = {
                                if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    onSendTestNotification()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("test_notif_button")
                        ) {
                            Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Probar ahora")
                        }
                    }
                }
            }
        }

        // Account & Sign Out
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = prefs.displayName,
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (!userEmail.isNullOrBlank()) {
                            Text(
                                text = userEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onSignOut,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salir")
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(76.dp)) }
    }

    if (pickingMorningTime) {
        val state = rememberTimePickerState(
            initialHour = prefs.morningHour.toInt(),
            initialMinute = prefs.morningMinute.toInt(),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { pickingMorningTime = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSaveSettings(
                            prefs.remindersEnabled,
                            state.hour.toLong(),
                            state.minute.toLong(),
                            prefs.eveningEnabled,
                            prefs.eveningHour,
                            prefs.eveningMinute,
                            prefs.toneEnum(),
                            prefs.dailyGoalCount
                        )
                        pickingMorningTime = false
                    }
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { pickingMorningTime = false }) { Text("Cancelar") }
            },
            title = { Text("Hora del aviso matutino") },
            text = { TimePicker(state = state) }
        )
    }

    if (pickingEveningTime) {
        val state = rememberTimePickerState(
            initialHour = prefs.eveningHour.toInt(),
            initialMinute = prefs.eveningMinute.toInt(),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { pickingEveningTime = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSaveSettings(
                            prefs.remindersEnabled,
                            prefs.morningHour,
                            prefs.morningMinute,
                            prefs.eveningEnabled,
                            state.hour.toLong(),
                            state.minute.toLong(),
                            prefs.toneEnum(),
                            prefs.dailyGoalCount
                        )
                        pickingEveningTime = false
                    }
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { pickingEveningTime = false }) { Text("Cancelar") }
            },
            title = { Text("Hora del cierre nocturno") },
            text = { TimePicker(state = state) }
        )
    }
}
