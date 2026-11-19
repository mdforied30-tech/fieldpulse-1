package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.AuthScreen
import com.example.auth.getSavedPortal
import com.example.auth.saveSelectedPortal
import com.example.auth.signOut
import com.example.data.model.AppMode
import com.example.data.model.SUPER_ADMIN_EMAIL
import com.example.data.repository.FieldPulseRepository
import com.example.ui.screens.AdminLiveMonitorScreen
import com.example.ui.screens.AdminTasksScreen
import com.example.ui.screens.AdminWorkersScreen
import com.example.ui.screens.WorkerBroadcasterScreen
import com.example.ui.screens.WorkerDashboardScreen
import com.example.ui.screens.WorkerProofLogsScreen
import com.example.ui.theme.Emerald400
import com.example.ui.theme.FieldPulseTheme
import com.example.viewmodel.MainViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FieldPulseTheme {
                AppNavigation()
            }
        }
    }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    var selectedPortal by remember { mutableStateOf(getSavedPortal(context)) }
    var authGateError by remember { mutableStateOf<String?>(null) }

    val user = currentUser
    val isAdminAccount =
        user != null && user.email.equals(SUPER_ADMIN_EMAIL, ignoreCase = true) && user.isEmailVerified

    val isUnauthorizedAdminPortal =
        user != null && selectedPortal == AppMode.ADMIN && !isAdminAccount

    LaunchedEffect(isUnauthorizedAdminPortal) {
        if (isUnauthorizedAdminPortal) {
            authGateError = "Access denied: Only Admin ($SUPER_ADMIN_EMAIL) can log in to the Admin Portal. Please use Workers Login."
            signOut(context, credentialManager, scope) {
                selectedPortal = null
            }
        }
    }

    if (user == null || isUnauthorizedAdminPortal) {
        AuthScreen(
            externalError = authGateError,
            onClearExternalError = { authGateError = null },
            onAuthSuccess = { portal ->
                saveSelectedPortal(context, portal)
                selectedPortal = portal
            }
        )
    } else {
        val resolvedInitialMode = when {
            selectedPortal == AppMode.WORKER -> AppMode.WORKER
            selectedPortal == AppMode.ADMIN && isAdminAccount -> AppMode.ADMIN
            isAdminAccount -> AppMode.ADMIN
            else -> AppMode.WORKER
        }
        key(user.uid, resolvedInitialMode) {
            val viewModel: MainViewModel = viewModel(
                key = "${user.uid}_${resolvedInitialMode.name}",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val dbId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(dbId)
                        MainViewModel(
                            repository = FieldPulseRepository(db),
                            currentUserId = user.uid,
                            userEmail = user.email.orEmpty(),
                            userDisplayName = user.displayName.orEmpty(),
                            isAdminAccount = isAdminAccount,
                            initialMode = resolvedInitialMode
                        )
                    }
                }
            )
            FieldPulseShell(
                viewModel = viewModel,
                onSignOutClick = {
                    signOut(context, credentialManager, scope) {
                        selectedPortal = null
                    }
                }
            )
        }
    }
}

private data class NavDestination(
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldPulseShell(
    viewModel: MainViewModel,
    onSignOutClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val appMode by viewModel.appMode.collectAsStateWithLifecycle()
    val selectedWorkerId by viewModel.selectedWorkerId.collectAsStateWithLifecycle()

    val myWorkerState by viewModel.myWorkerProfile.collectAsStateWithLifecycle()
    val myTasksState by viewModel.myTasks.collectAsStateWithLifecycle()
    val myMediaLogsState by viewModel.myMediaLogs.collectAsStateWithLifecycle()
    val allWorkersState by viewModel.allWorkers.collectAsStateWithLifecycle()
    val allTasksState by viewModel.allTasks.collectAsStateWithLifecycle()

    val selectedWorkerState by viewModel.selectedWorkerProfile.collectAsStateWithLifecycle()
    val selectedTelemetryState by viewModel.selectedWorkerTelemetry.collectAsStateWithLifecycle()
    val selectedLocationLogsState by viewModel.selectedWorkerLocationLogs.collectAsStateWithLifecycle()
    val selectedMediaLogsState by viewModel.selectedWorkerMediaLogs.collectAsStateWithLifecycle()

    var adminTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var workerTabIndex by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.toastEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val adminDestinations = listOf(
        NavDestination(stringResource(R.string.nav_live_monitor), Icons.Filled.Sensors, "nav_live_monitor"),
        NavDestination(stringResource(R.string.nav_tasks), Icons.Filled.Assignment, "nav_admin_tasks"),
        NavDestination(stringResource(R.string.nav_workers), Icons.Filled.Group, "nav_admin_workers")
    )

    val workerDestinations = listOf(
        NavDestination(stringResource(R.string.nav_dashboard), Icons.Filled.Engineering, "nav_worker_desk"),
        NavDestination(stringResource(R.string.nav_broadcaster), Icons.Filled.Videocam, "nav_worker_feed"),
        NavDestination(stringResource(R.string.nav_logs), Icons.Filled.FactCheck, "nav_worker_logs")
    )

    val currentDestinations = if (appMode == AppMode.ADMIN) adminDestinations else workerDestinations
    val currentTab = if (appMode == AppMode.ADMIN) adminTabIndex else workerTabIndex

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 680.dp

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = if (appMode == AppMode.ADMIN) "ADMIN" else "WORKER",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Emerald400,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    },
                    actions = {
                        if (viewModel.isAdminAccount) {
                            FilterChip(
                                selected = appMode == AppMode.WORKER,
                                onClick = {
                                    val next = if (appMode == AppMode.ADMIN) AppMode.WORKER else AppMode.ADMIN
                                    viewModel.switchMode(next)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.SwapHoriz,
                                        contentDescription = "Switch mode"
                                    )
                                },
                                label = {
                                    Text(if (appMode == AppMode.ADMIN) "Worker View" else "Admin View")
                                },
                                modifier = Modifier.testTag("mode_switcher_chip")
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        IconButton(
                            onClick = onSignOutClick,
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Sign out"
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar {
                        currentDestinations.forEachIndexed { index, dest ->
                            NavigationBarItem(
                                selected = currentTab == index,
                                onClick = {
                                    if (appMode == AppMode.ADMIN) {
                                        adminTabIndex = index
                                        if (index == 0) viewModel.selectWorkerForLiveMonitor(null)
                                    } else {
                                        workerTabIndex = index
                                    }
                                },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
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
                if (useNavRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        currentDestinations.forEachIndexed { index, dest ->
                            NavigationRailItem(
                                selected = currentTab == index,
                                onClick = {
                                    if (appMode == AppMode.ADMIN) {
                                        adminTabIndex = index
                                        if (index == 0) viewModel.selectWorkerForLiveMonitor(null)
                                    } else {
                                        workerTabIndex = index
                                    }
                                },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 900.dp)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.Top
                    ) {
                        if (appMode == AppMode.ADMIN) {
                            when (adminTabIndex) {
                                0 -> AdminLiveMonitorScreen(
                                    workersState = allWorkersState,
                                    tasksState = allTasksState,
                                    selectedWorkerId = selectedWorkerId,
                                    selectedWorkerState = selectedWorkerState,
                                    selectedTelemetryState = selectedTelemetryState,
                                    selectedLocationLogsState = selectedLocationLogsState,
                                    selectedMediaLogsState = selectedMediaLogsState,
                                    onSelectWorker = viewModel::selectWorkerForLiveMonitor,
                                    onSwitchWorkerCamera = viewModel::adminSwitchWorkerCamera,
                                    onSwitchToWorkerPreview = { viewModel.switchMode(AppMode.WORKER) }
                                )

                                1 -> AdminTasksScreen(
                                    tasksState = allTasksState,
                                    workersState = allWorkersState,
                                    onCreateTask = viewModel::createTask,
                                    onDeleteTask = viewModel::deleteTask
                                )

                                else -> AdminWorkersScreen(
                                    workersState = allWorkersState,
                                    onSelectWorker = { wId ->
                                        viewModel.selectWorkerForLiveMonitor(wId)
                                        adminTabIndex = 0
                                    }
                                )
                            }
                        } else {
                            when (workerTabIndex) {
                                0 -> WorkerDashboardScreen(
                                    workerState = myWorkerState,
                                    tasksState = myTasksState,
                                    defaultName = viewModel.userDisplayName,
                                    onRegisterOrUpdateProfile = { name, phone, dept, perms ->
                                        viewModel.registerOrUpdateWorker(
                                            name = name,
                                            phone = phone,
                                            department = dept,
                                            locationGranted = perms.locationGranted,
                                            backgroundLocationGranted = perms.backgroundLocationGranted,
                                            cameraGranted = perms.cameraGranted,
                                            micGranted = perms.micGranted
                                        )
                                    },
                                    onSyncPermissions = { perms ->
                                        viewModel.syncPermissions(
                                            locationGranted = perms.locationGranted,
                                            backgroundLocationGranted = perms.backgroundLocationGranted,
                                            cameraGranted = perms.cameraGranted,
                                            micGranted = perms.micGranted
                                        )
                                    },
                                    onUpdateTaskStatus = viewModel::updateTaskStatus,
                                    onAddProofLog = viewModel::addWorkProofLog
                                )

                                1 -> WorkerBroadcasterScreen(
                                    workerState = myWorkerState,
                                    onToggleCameraBroadcast = viewModel::toggleWorkerCameraStream,
                                    onToggleMicBroadcast = viewModel::toggleWorkerMicStream,
                                    onPublishFrame = viewModel::publishCameraFrame
                                )

                                else -> WorkerProofLogsScreen(
                                    mediaLogsState = myMediaLogsState
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
