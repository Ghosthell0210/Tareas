package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.ai.SmartPrioritizerService
import com.example.data.firebase.signOut
import com.example.data.model.DailyTask
import com.example.data.repository.PulsoRepository
import com.example.ui.components.TaskEditorSheet
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.PlannerScreen
import com.example.ui.screens.RemindersScreen
import com.example.ui.screens.TodayDashboardScreen
import com.example.ui.viewmodel.AndroidReminderSyncGateway
import com.example.ui.viewmodel.PulsoViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private enum class PulsoTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    TODAY("today", "Hoy", Icons.Rounded.Today, Icons.Outlined.Today),
    PLANNER("planner", "Agenda", Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth),
    REMINDERS("reminders", "Alertas", Icons.Rounded.NotificationsActive, Icons.Outlined.Notifications)
}

@Composable
fun PulsoApp() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
            }
        )
    } else {
        key(user.uid) {
            AuthenticatedPulsoShell(
                currentUserId = user.uid,
                displayName = user.displayName ?: "Usuario",
                userEmail = user.email
            )
        }
    }
}

@Composable
private fun AuthenticatedPulsoShell(
    currentUserId: String,
    displayName: String,
    userEmail: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val viewModel: PulsoViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                PulsoViewModel(
                    repository = PulsoRepository(db),
                    aiService = SmartPrioritizerService(),
                    reminderGateway = AndroidReminderSyncGateway(app.applicationContext),
                    currentUserId = currentUserId,
                    displayName = displayName
                )
            }
        }
    )

    val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
    val prefsState by viewModel.preferencesState.collectAsStateWithLifecycle()
    val aiStatus by viewModel.aiStatus.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: PulsoTab.TODAY.route

    var showEditorSheet by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<DailyTask?>(null) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            floatingActionButton = {
                if (currentRoute != PulsoTab.REMINDERS.route) {
                    FloatingActionButton(
                        onClick = {
                            editingTask = null
                            showEditorSheet = true
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("add_task_fab")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Agregar tarea"
                        )
                    }
                }
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        PulsoTab.entries.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(PulsoTab.TODAY.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        PulsoTab.entries.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(PulsoTab.TODAY.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("rail_${tab.route}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = PulsoTab.TODAY.route,
                        modifier = Modifier.widthIn(max = 720.dp)
                    ) {
                        composable(PulsoTab.TODAY.route) {
                            TodayDashboardScreen(
                                tasksState = tasksState,
                                preferencesState = prefsState,
                                aiStatus = aiStatus,
                                onRunAiPrioritization = { viewModel.runAiPrioritization() },
                                onToggleComplete = { viewModel.toggleTaskCompletion(it) },
                                onEditTask = {
                                    editingTask = it
                                    showEditorSheet = true
                                },
                                onDeleteTask = { viewModel.deleteTask(it) },
                                onAddNewTask = {
                                    editingTask = null
                                    showEditorSheet = true
                                }
                            )
                        }
                        composable(PulsoTab.PLANNER.route) {
                            PlannerScreen(
                                tasksState = tasksState,
                                onToggleComplete = { viewModel.toggleTaskCompletion(it) },
                                onEditTask = {
                                    editingTask = it
                                    showEditorSheet = true
                                },
                                onDeleteTask = { viewModel.deleteTask(it) }
                            )
                        }
                        composable(PulsoTab.REMINDERS.route) {
                            RemindersScreen(
                                preferencesState = prefsState,
                                aiStatus = aiStatus,
                                userEmail = userEmail,
                                onSaveSettings = { enabled, mh, mm, evEnabled, eh, em, tone, goal ->
                                    viewModel.updateReminderSettings(
                                        remindersEnabled = enabled,
                                        morningHour = mh,
                                        morningMinute = mm,
                                        eveningEnabled = evEnabled,
                                        eveningHour = eh,
                                        eveningMinute = em,
                                        tone = tone,
                                        dailyGoalCount = goal
                                    )
                                },
                                onGenerateAiCopy = { viewModel.refreshPersonalizedNotification() },
                                onSendTestNotification = { viewModel.sendTestNotification() },
                                onSignOut = {
                                    signOut(credentialManager = credentialManager, scope = scope)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditorSheet) {
        TaskEditorSheet(
            editingTask = editingTask,
            onDismiss = { showEditorSheet = false },
            onSave = { title, notes, category, urgency, timeWindow, mins, dueEpoch, recurring, remEnabled, remH, remM ->
                val target = editingTask
                if (target == null) {
                    viewModel.addTask(
                        title = title,
                        notes = notes,
                        category = category,
                        urgency = urgency,
                        timeWindow = timeWindow,
                        estimatedMinutes = mins,
                        dueDateEpochDay = dueEpoch,
                        dailyRecurring = recurring,
                        reminderEnabled = remEnabled,
                        reminderHour = remH,
                        reminderMinute = remM
                    )
                } else {
                    viewModel.updateTask(
                        task = target,
                        title = title,
                        notes = notes,
                        category = category,
                        urgency = urgency,
                        timeWindow = timeWindow,
                        estimatedMinutes = mins,
                        dueDateEpochDay = dueEpoch,
                        dailyRecurring = recurring,
                        reminderEnabled = remEnabled,
                        reminderHour = remH,
                        reminderMinute = remM
                    )
                }
                showEditorSheet = false
            }
        )
    }
}
