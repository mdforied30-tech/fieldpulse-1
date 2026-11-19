package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CameraFacing
import com.example.data.model.FieldTask
import com.example.data.model.LocationLog
import com.example.data.model.MediaLog
import com.example.data.model.PermissionSyncStatus
import com.example.data.model.TaskPriority
import com.example.data.model.TaskStatus
import com.example.data.model.UiState
import com.example.data.model.WorkerProfile
import com.example.data.model.WorkerTelemetry
import com.example.ui.components.LiveAudioVisualizer
import com.example.ui.components.LiveCameraFeedPlayer
import com.example.ui.components.LiveGpsRadarMap
import com.example.ui.components.PermissionStatusBadges
import com.example.ui.components.decodeBase64Bitmap
import com.example.ui.theme.Amber400
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Emerald500

@Composable
fun AdminLiveMonitorScreen(
    workersState: UiState<List<WorkerProfile>>,
    tasksState: UiState<List<FieldTask>>,
    selectedWorkerId: String?,
    selectedWorkerState: UiState<WorkerProfile?>,
    selectedTelemetryState: UiState<WorkerTelemetry?>,
    selectedLocationLogsState: UiState<List<LocationLog>>,
    selectedMediaLogsState: UiState<List<MediaLog>>,
    onSelectWorker: (String?) -> Unit,
    onSwitchWorkerCamera: (String, CameraFacing) -> Unit,
    onSwitchToWorkerPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selectedWorkerId != null) {
        BackHandler { onSelectWorker(null) }
        UnifiedWorkerControlCenter(
            workerId = selectedWorkerId,
            workerState = selectedWorkerState,
            telemetryState = selectedTelemetryState,
            locationLogsState = selectedLocationLogsState,
            mediaLogsState = selectedMediaLogsState,
            tasksState = tasksState,
            onBack = { onSelectWorker(null) },
            onSwitchWorkerCamera = onSwitchWorkerCamera,
            modifier = modifier
        )
        return
    }

    val workers = (workersState as? UiState.Success)?.data.orEmpty()
    val tasks = (tasksState as? UiState.Success)?.data.orEmpty()
    val readyCount = workers.count { it.permissionStatus == PermissionSyncStatus.ALL_GRANTED.name }
    val activeServiceCount = workers.count { it.serviceRunning }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "LIVE MONITOR",
                    style = MaterialTheme.typography.labelMedium,
                    color = Emerald400
                )
                Text(
                    text = "All-in-One Control Center",
                    style = MaterialTheme.typography.headlineMedium
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile(
                    label = "Registered",
                    value = workers.size.toString(),
                    accent = Cyan400,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "ALL GRANTED",
                    value = readyCount.toString(),
                    accent = Emerald400,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Active FGS",
                    value = activeServiceCount.toString(),
                    accent = Amber400,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        when (workersState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
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
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = workersState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            is UiState.Success -> {
                if (workers.isEmpty()) {
                    item {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Engineering,
                                    contentDescription = "No workers yet",
                                    tint = Cyan400,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = "No Active Workers Connected Yet",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Workers appear here automatically once they sign in and complete onboarding. You can also switch to Worker Mode to test live GPS, Camera, and Mic streaming from this device.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = onSwitchToWorkerPreview,
                                    modifier = Modifier.testTag("open_worker_preview_button")
                                ) {
                                    Text("Open Worker Mode on This Device")
                                }
                            }
                        }
                    }
                } else {
                    items(workers, key = { it.userId }) { worker ->
                        val activeTask = tasks.firstOrNull {
                            it.assignedWorkerId == worker.userId && it.status != TaskStatus.COMPLETED.name
                        }
                        WorkerMonitorCard(
                            worker = worker,
                            activeTask = activeTask,
                            onClick = { onSelectWorker(worker.userId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkerMonitorCard(
    worker: WorkerProfile,
    activeTask: FieldTask?,
    onClick: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .testTag("worker_monitor_card_${worker.userId}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = CircleShape,
                        color = if (worker.serviceRunning) Emerald500.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = "Worker avatar",
                                tint = if (worker.serviceRunning) Emerald400 else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = worker.name.ifBlank { worker.email },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${worker.department} • ${if (worker.serviceRunning) "LIVE SERVICE" else "STANDBY"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (worker.serviceRunning) Emerald400 else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Visibility,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Live View",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            PermissionStatusBadges(
                locationGranted = worker.locationGranted,
                backgroundLocationGranted = worker.backgroundLocationGranted,
                cameraGranted = worker.cameraGranted,
                micGranted = worker.micGranted,
                permissionStatus = worker.permissionStatus
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val gpsLabel = if (worker.latitude != null && worker.longitude != null) {
                    String.format("GPS %.4f, %.4f", worker.latitude, worker.longitude)
                } else {
                    "No GPS fix yet"
                }
                Text(
                    text = gpsLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = Cyan400
                )
                Text(
                    text = activeTask?.title ?: "No active task",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun UnifiedWorkerControlCenter(
    workerId: String,
    workerState: UiState<WorkerProfile?>,
    telemetryState: UiState<WorkerTelemetry?>,
    locationLogsState: UiState<List<LocationLog>>,
    mediaLogsState: UiState<List<MediaLog>>,
    tasksState: UiState<List<FieldTask>>,
    onBack: () -> Unit,
    onSwitchWorkerCamera: (String, CameraFacing) -> Unit,
    modifier: Modifier = Modifier
) {
    val worker = (workerState as? UiState.Success)?.data
    val telemetry = (telemetryState as? UiState.Success)?.data
    val locationLogs = (locationLogsState as? UiState.Success)?.data.orEmpty()
    val mediaLogs = (mediaLogsState as? UiState.Success)?.data.orEmpty()
    val workerTasks = (tasksState as? UiState.Success)?.data.orEmpty()
        .filter { it.assignedWorkerId == workerId }

    var audioMutedLocally by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("control_center_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Live Monitor"
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "UNIFIED CONTROL CENTER",
                    style = MaterialTheme.typography.labelMedium,
                    color = Emerald400
                )
                Text(
                    text = worker?.name?.ifBlank { worker.email } ?: "Loading worker...",
                    style = MaterialTheme.typography.titleLarge
                )
            }
            if (worker != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (worker.serviceRunning) Emerald500.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        text = if (worker.serviceRunning) "● ONLINE" else "IDLE",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (worker.serviceRunning) Emerald400 else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        if (worker != null) {
            PermissionStatusBadges(
                locationGranted = worker.locationGranted,
                backgroundLocationGranted = worker.backgroundLocationGranted,
                cameraGranted = worker.cameraGranted,
                micGranted = worker.micGranted,
                permissionStatus = worker.permissionStatus
            )

            // 1. Live GPS Map
            SectionHeader(title = "1. Live GPS Map", subtitle = "Real-time worker beacon & route trail")
            LiveGpsRadarMap(
                latitude = worker.latitude,
                longitude = worker.longitude,
                accuracyMeters = worker.accuracyMeters,
                speedMps = worker.speedMps,
                locationLogs = locationLogs,
                workerName = worker.name.ifBlank { "Worker" }
            )

            // 2. Live Camera Feed
            SectionHeader(title = "2. Live Camera Feed", subtitle = "Remote Front/Back lens switch synced")
            val activeFacing = telemetry?.cameraFacing ?: worker.activeCameraFacing
            val nextFacing = if (activeFacing == CameraFacing.BACK.name) CameraFacing.FRONT else CameraFacing.BACK
            LiveCameraFeedPlayer(
                frameBase64 = telemetry?.cameraFrameBase64,
                cameraFacing = activeFacing,
                streamingVideo = telemetry?.streamingVideo ?: worker.cameraStreamEnabled,
                onSwitchCameraClick = { onSwitchWorkerCamera(workerId, nextFacing) }
            )

            // 3. Live Microphone Audio
            SectionHeader(title = "3. Live Microphone Audio", subtitle = "Real-time dBFS waveform & mute monitor")
            LiveAudioVisualizer(
                audioLevelDb = telemetry?.audioLevelDb ?: -60.0,
                audioBars = telemetry?.audioBars.orEmpty(),
                streamingAudio = telemetry?.streamingAudio ?: worker.micStreamEnabled,
                mutedLocally = audioMutedLocally,
                onToggleMute = { audioMutedLocally = !audioMutedLocally }
            )

            // 4. Live Work & Task Status
            SectionHeader(title = "4. Live Work & Task Status", subtitle = "Assigned tasks and proof logs")
            if (workerTasks.isEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No tasks assigned to this worker yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                workerTasks.forEach { task ->
                    TaskStatusCard(task = task, onDelete = null)
                }
            }

            if (mediaLogs.isNotEmpty()) {
                Text(
                    text = "Recent Work Proof Logs (${mediaLogs.size})",
                    style = MaterialTheme.typography.titleSmall
                )
                mediaLogs.take(5).forEach { log ->
                    MediaLogCard(log = log)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminTasksScreen(
    tasksState: UiState<List<FieldTask>>,
    workersState: UiState<List<WorkerProfile>>,
    onCreateTask: (String, String, WorkerProfile, TaskPriority, String) -> Unit,
    onDeleteTask: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tasks = (tasksState as? UiState.Success)?.data.orEmpty()
    val workers = (workersState as? UiState.Success)?.data.orEmpty()
    var filterStatus by remember { mutableStateOf<TaskStatus?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val filteredTasks = if (filterStatus == null) tasks else tasks.filter { it.status == filterStatus?.name }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "DISPATCH & WORKFLOW",
                    style = MaterialTheme.typography.labelMedium,
                    color = Emerald400
                )
                Text(
                    text = "Task Management",
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filterStatus == null,
                        onClick = { filterStatus = null },
                        label = { Text("All (${tasks.size})") }
                    )
                    TaskStatus.entries.forEach { st ->
                        FilterChip(
                            selected = filterStatus == st,
                            onClick = { filterStatus = st },
                            label = { Text(st.name.replace("_", " ")) }
                        )
                    }
                }
            }

            if (filteredTasks.isEmpty()) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("No tasks match this filter", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Tap + Assign Task to dispatch work to any registered worker.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredTasks, key = { it.id }) { task ->
                    TaskStatusCard(
                        task = task,
                        onDelete = { onDeleteTask(task.id) }
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(72.dp)) }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("create_task_fab")
        ) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = "Assign new task")
        }
    }

    if (showCreateDialog) {
        CreateTaskDialog(
            workers = workers,
            onDismiss = { showCreateDialog = false },
            onSubmit = { title, desc, worker, priority, loc ->
                onCreateTask(title, desc, worker, priority, loc)
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun AdminWorkersScreen(
    workersState: UiState<List<WorkerProfile>>,
    onSelectWorker: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val workers = (workersState as? UiState.Success)?.data.orEmpty()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "WORKFORCE ROSTER",
                style = MaterialTheme.typography.labelMedium,
                color = Cyan400
            )
            Text(
                text = "Registered Workers (${workers.size})",
                style = MaterialTheme.typography.headlineMedium
            )
        }
        items(workers, key = { it.userId }) { worker ->
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectWorker(worker.userId) }
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = worker.name.ifBlank { worker.email },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${worker.email} • ${worker.phone.ifBlank { "No phone" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = worker.role.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Cyan400
                        )
                    }
                    PermissionStatusBadges(
                        locationGranted = worker.locationGranted,
                        backgroundLocationGranted = worker.backgroundLocationGranted,
                        cameraGranted = worker.cameraGranted,
                        micGranted = worker.micGranted,
                        permissionStatus = worker.permissionStatus
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateTaskDialog(
    workers: List<WorkerProfile>,
    onDismiss: () -> Unit,
    onSubmit: (String, String, WorkerProfile, TaskPriority, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var locationLabel by remember { mutableStateOf("") }
    var selectedPriority by remember { mutableStateOf(TaskPriority.HIGH) }
    var selectedWorker by remember(workers) { mutableStateOf(workers.firstOrNull()) }
    var workerMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign Field Task") },
        text = {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (workers.isEmpty()) {
                    Text(
                        text = "No registered workers available yet. Switch to Worker Mode or wait for a worker to register.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = workerMenuExpanded,
                        onExpandedChange = { workerMenuExpanded = !workerMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedWorker?.let { "${it.name} (${it.department})" } ?: "Select Worker",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Assign To") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = workerMenuExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = workerMenuExpanded,
                            onDismissRequest = { workerMenuExpanded = false }
                        ) {
                            workers.forEach { w ->
                                DropdownMenuItem(
                                    text = { Text("${w.name.ifBlank { w.email }} • ${w.department}") },
                                    onClick = {
                                        selectedWorker = w
                                        workerMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("task_title_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Instructions") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("task_desc_input")
                )
                OutlinedTextField(
                    value = locationLabel,
                    onValueChange = { locationLabel = it },
                    label = { Text("Site / Zone Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TaskPriority.entries.forEach { p ->
                        FilterChip(
                            selected = selectedPriority == p,
                            onClick = { selectedPriority = p },
                            label = { Text(p.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val w = selectedWorker
                    if (w != null && title.isNotBlank()) {
                        onSubmit(title, description, w, selectedPriority, locationLabel)
                    }
                },
                enabled = selectedWorker != null && title.isNotBlank(),
                modifier = Modifier.testTag("confirm_create_task_button")
            ) {
                Text("Assign")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun TaskStatusCard(
    task: FieldTask,
    onDelete: (() -> Unit)?
) {
    val statusColor = when (TaskStatus.fromRaw(task.status)) {
        TaskStatus.COMPLETED -> Emerald400
        TaskStatus.IN_PROGRESS -> Cyan400
        TaskStatus.PENDING -> Amber400
    }
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (task.status == TaskStatus.COMPLETED.name) Icons.Filled.CheckCircle else Icons.Filled.Assignment,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Delete task",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (task.description.isNotBlank()) {
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${task.assignedWorkerName} • ${task.locationLabel.ifBlank { "Field Site" }}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${task.priority} • ${task.status}",
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
            }
            if (task.workerNotes.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Worker Note: ${task.workerNotes}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MediaLogCard(log: MediaLog) {
    val bmp = remember(log.thumbnailBase64) { decodeBase64Bitmap(log.thumbnailBase64) }
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = "Work proof thumbnail",
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = log.mediaType,
                    style = MaterialTheme.typography.labelSmall,
                    color = Cyan400
                )
                Text(
                    text = log.note,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (log.latitude != null && log.longitude != null) {
                    Text(
                        text = String.format("GPS %.4f, %.4f", log.latitude, log.longitude),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = accent,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
