package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CameraFacing
import com.example.data.model.FieldTask
import com.example.data.model.MediaLog
import com.example.data.model.MediaLogType
import com.example.data.model.PermissionSyncStatus
import com.example.data.model.TaskStatus
import com.example.data.model.UiState
import com.example.data.model.WorkerProfile
import com.example.service.WorkerMonitoringService
import com.example.ui.components.CameraXPreviewBroadcaster
import com.example.ui.components.LiveAudioVisualizer
import com.example.ui.components.PermissionStatusBadges
import com.example.ui.components.decodeBase64Bitmap
import com.example.ui.theme.Amber400
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Emerald500
import java.io.ByteArrayOutputStream

data class LocalPermissionState(
    val locationGranted: Boolean,
    val backgroundLocationGranted: Boolean,
    val cameraGranted: Boolean,
    val micGranted: Boolean,
    val notificationsGranted: Boolean
)

fun readLocalPermissions(context: Context): LocalPermissionState {
    fun granted(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
    val bg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    } else true
    val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        granted(Manifest.permission.POST_NOTIFICATIONS)
    } else true

    return LocalPermissionState(
        locationGranted = fine || coarse,
        backgroundLocationGranted = bg,
        cameraGranted = granted(Manifest.permission.CAMERA),
        micGranted = granted(Manifest.permission.RECORD_AUDIO),
        notificationsGranted = notif
    )
}

@Composable
fun WorkerDashboardScreen(
    workerState: UiState<WorkerProfile?>,
    tasksState: UiState<List<FieldTask>>,
    defaultName: String,
    portalEmail: String,
    portalPhone: String,
    portalDept: String,
    onRegisterOrUpdateProfile: (String, String, String, LocalPermissionState, String?) -> Unit,
    onSyncPermissions: (LocalPermissionState) -> Unit,
    onToggleCameraBroadcast: (Boolean, CameraFacing) -> Unit,
    onToggleMicBroadcast: (Boolean) -> Unit,
    onPublishFrame: (String, CameraFacing, Double, List<Double>, Boolean) -> Unit,
    onUpdateTaskStatus: (String, TaskStatus, String) -> Unit,
    onAddProofLog: (String?, String, MediaLogType, String?, Double?, Double?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val serviceTelemetry by WorkerMonitoringService.telemetryState.collectAsStateWithLifecycle()
    var localPerms by remember { mutableStateOf(readLocalPermissions(context)) }
    val worker = (workerState as? UiState.Success)?.data
    val tasks = (tasksState as? UiState.Success)?.data.orEmpty()

    var proofTaskForDialog by remember { mutableStateOf<FieldTask?>(null) }
    var showGeneralProofDialog by remember { mutableStateOf(false) }

    var userPausedCamera by rememberSaveable { mutableStateOf(false) }
    val requestedFacing = CameraFacing.fromRaw(worker?.requestedCameraFacing)
    var activeFacing by remember { mutableStateOf(requestedFacing) }

    val cameraBroadcasting = worker != null && localPerms.cameraGranted && !userPausedCamera

    fun ensureMonitoringStarted(perms: LocalPermissionState) {
        if ((perms.locationGranted || perms.cameraGranted || perms.micGranted) &&
            !WorkerMonitoringService.telemetryState.value.running
        ) {
            WorkerMonitoringService.start(context)
        }
        if (perms.micGranted) {
            WorkerMonitoringService.setMicEnabled(context, true)
            onToggleMicBroadcast(true)
        }
        if (perms.cameraGranted && !userPausedCamera) {
            onToggleCameraBroadcast(true, activeFacing)
        }
    }

    val bgLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        localPerms = readLocalPermissions(context)
        if (worker == null) {
            onRegisterOrUpdateProfile(
                defaultName.ifBlank { "Field Worker" },
                portalPhone,
                portalDept.ifBlank { "Field Operations" },
                localPerms,
                portalEmail.takeIf { it.isNotBlank() }
            )
        } else {
            onSyncPermissions(localPerms)
        }
        ensureMonitoringStarted(localPerms)
    }

    val corePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        localPerms = readLocalPermissions(context)
        if (worker == null) {
            onRegisterOrUpdateProfile(
                defaultName.ifBlank { "Field Worker" },
                portalPhone,
                portalDept.ifBlank { "Field Operations" },
                localPerms,
                portalEmail.takeIf { it.isNotBlank() }
            )
        } else {
            onSyncPermissions(localPerms)
        }
        ensureMonitoringStarted(localPerms)

        if (localPerms.locationGranted &&
            !localPerms.backgroundLocationGranted &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        ) {
            bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    // Auto-register worker profile if logged in via Worker Portal form & sync existing permissions
    LaunchedEffect(workerState, portalEmail, defaultName) {
        localPerms = readLocalPermissions(context)
        if (workerState is UiState.Success && worker == null && defaultName.isNotBlank()) {
            onRegisterOrUpdateProfile(
                defaultName,
                portalPhone,
                portalDept.ifBlank { "Field Operations" },
                localPerms,
                portalEmail.takeIf { it.isNotBlank() }
            )
        } else if (worker != null) {
            onSyncPermissions(localPerms)
            ensureMonitoringStarted(localPerms)
        }
    }

    // Sync Admin's remote camera facing requests (Front/Back switch) immediately
    LaunchedEffect(worker?.requestedCameraFacing) {
        val req = CameraFacing.fromRaw(worker?.requestedCameraFacing)
        if (req != activeFacing) {
            activeFacing = req
            onToggleCameraBroadcast(cameraBroadcasting, req)
        }
    }

    LaunchedEffect(cameraBroadcasting, activeFacing) {
        WorkerMonitoringService.isVideoStreamingFromUi = cameraBroadcasting
        WorkerMonitoringService.activeCameraFacingForService = activeFacing
        if (worker != null && localPerms.cameraGranted) {
            onToggleCameraBroadcast(cameraBroadcasting, activeFacing)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "WORKER FIELD DESK",
                style = MaterialTheme.typography.labelMedium,
                color = Emerald400
            )
            Text(
                text = worker?.name?.ifBlank { defaultName.ifBlank { "Worker Portal" } }
                    ?: defaultName.ifBlank { "Worker Portal" },
                style = MaterialTheme.typography.headlineMedium
            )
            if (portalEmail.isNotBlank()) {
                Text(
                    text = portalEmail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Always show "Allow All" Permission Onboarding immediately upon entering Workers Portal
        item {
            PermissionOnboardingCard(
                localPerms = localPerms,
                syncedStatus = worker?.permissionStatus ?: if (
                    localPerms.locationGranted && localPerms.cameraGranted && localPerms.micGranted
                ) PermissionSyncStatus.ALL_GRANTED.name else PermissionSyncStatus.PENDING.name,
                onAllowAllClick = {
                    val perms = buildList {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                        add(Manifest.permission.CAMERA)
                        add(Manifest.permission.RECORD_AUDIO)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    corePermissionsLauncher.launch(perms.toTypedArray())
                },
                onRequestBackgroundLocation = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                }
            )
        }

        if (worker == null && workerState is UiState.Success && defaultName.isBlank()) {
            item {
                WorkerRegistrationCard(
                    defaultName = defaultName,
                    defaultPhone = portalPhone,
                    defaultDept = portalDept,
                    onSubmit = { name, phone, dept ->
                        onRegisterOrUpdateProfile(name, phone, dept, localPerms, portalEmail.takeIf { it.isNotBlank() })
                    }
                )
            }
        } else if (worker != null) {

            // Persistent Foreground Service GPS + Audio Card
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth()
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Live GPS & Audio Sync",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (serviceTelemetry.running) {
                                        "Sharing GPS & Mic • ${serviceTelemetry.uptimeSeconds}s active"
                                    } else {
                                        "Tap Start Live to transmit GPS & Mic"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (serviceTelemetry.running) Emerald400 else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = {
                                    if (serviceTelemetry.running) {
                                        WorkerMonitoringService.stop(context)
                                    } else {
                                        WorkerMonitoringService.start(context)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (serviceTelemetry.running) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("toggle_foreground_service_button")
                            ) {
                                Icon(
                                    imageVector = if (serviceTelemetry.running) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (serviceTelemetry.running) "Stop" else "Start Live")
                            }
                        }

                        val lat = serviceTelemetry.latitude ?: worker.latitude
                        val lon = serviceTelemetry.longitude ?: worker.longitude
                        Text(
                            text = if (lat != null && lon != null) {
                                String.format("GPS Fix: %.5f, %.5f", lat, lon)
                            } else {
                                "GPS Fix: Acquiring location lock..."
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = Cyan400
                        )

                        if (localPerms.micGranted) {
                            LiveAudioVisualizer(
                                audioLevelDb = serviceTelemetry.audioLevelDb,
                                audioBars = serviceTelemetry.audioBars,
                                streamingAudio = serviceTelemetry.micCaptureActive,
                                mutedLocally = false,
                                onToggleMute = {}
                            )
                        }
                    }
                }
            }

            // Live Camera Sharing Card right on Field Desk so video frames stream immediately
            if (localPerms.cameraGranted) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.fillMaxWidth()
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Videocam,
                                        contentDescription = null,
                                        tint = if (cameraBroadcasting) Emerald400 else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Live Camera Sharing",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (cameraBroadcasting) {
                                                "Streaming ${activeFacing.name} lens to Admin"
                                            } else {
                                                "Camera sharing paused"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (cameraBroadcasting) Emerald400 else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = cameraBroadcasting,
                                    onCheckedChange = { enabled ->
                                        userPausedCamera = !enabled
                                        onToggleCameraBroadcast(enabled, activeFacing)
                                    },
                                    modifier = Modifier.testTag("desk_camera_broadcast_switch")
                                )
                            }

                            CameraXPreviewBroadcaster(
                                cameraFacing = activeFacing,
                                streamingEnabled = cameraBroadcasting,
                                onFrameCaptured = { base64 ->
                                    onPublishFrame(
                                        base64,
                                        activeFacing,
                                        serviceTelemetry.audioLevelDb,
                                        serviceTelemetry.audioBars,
                                        serviceTelemetry.micCaptureActive
                                    )
                                },
                                previewHeightDp = 160
                            )
                        }
                    }
                }
            }

            // Assigned Tasks Dashboard
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Assigned Tasks (${tasks.size})",
                        style = MaterialTheme.typography.titleLarge
                    )
                    FilledTonalButton(
                        onClick = { showGeneralProofDialog = true },
                        modifier = Modifier.testTag("add_work_proof_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddAPhoto,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Log Proof")
                    }
                }
            }

            if (tasks.isEmpty()) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No tasks assigned yet. Once Admin dispatches a job, it will appear here instantaneously.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(tasks, key = { it.id }) { task ->
                    WorkerTaskActionCard(
                        task = task,
                        onStartJob = { onUpdateTaskStatus(task.id, TaskStatus.IN_PROGRESS, task.workerNotes) },
                        onCompleteJob = { onUpdateTaskStatus(task.id, TaskStatus.COMPLETED, task.workerNotes) },
                        onLogProof = { proofTaskForDialog = task }
                    )
                }
            }
        }
    }

    if (proofTaskForDialog != null || showGeneralProofDialog) {
        AddProofLogDialog(
            task = proofTaskForDialog,
            onDismiss = {
                proofTaskForDialog = null
                showGeneralProofDialog = false
            },
            onSubmit = { note, type, thumbBase64 ->
                onAddProofLog(
                    proofTaskForDialog?.id,
                    note,
                    type,
                    thumbBase64,
                    serviceTelemetry.latitude ?: worker?.latitude,
                    serviceTelemetry.longitude ?: worker?.longitude
                )
                proofTaskForDialog = null
                showGeneralProofDialog = false
            }
        )
    }
}

@Composable
fun WorkerBroadcasterScreen(
    workerState: UiState<WorkerProfile?>,
    onToggleCameraBroadcast: (Boolean, CameraFacing) -> Unit,
    onToggleMicBroadcast: (Boolean) -> Unit,
    onPublishFrame: (String, CameraFacing, Double, List<Double>, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val worker = (workerState as? UiState.Success)?.data
    val serviceTelemetry by WorkerMonitoringService.telemetryState.collectAsStateWithLifecycle()
    val localPerms = remember(worker) { readLocalPermissions(context) }

    val requestedFacing = CameraFacing.fromRaw(worker?.requestedCameraFacing)
    var activeFacing by remember { mutableStateOf(requestedFacing) }
    var cameraBroadcasting by remember(worker?.cameraStreamEnabled, localPerms.cameraGranted) {
        mutableStateOf(worker?.cameraStreamEnabled ?: localPerms.cameraGranted)
    }

    LaunchedEffect(worker?.requestedCameraFacing) {
        val req = CameraFacing.fromRaw(worker?.requestedCameraFacing)
        if (req != activeFacing) {
            activeFacing = req
            onToggleCameraBroadcast(cameraBroadcasting, req)
        }
    }

    LaunchedEffect(cameraBroadcasting, activeFacing) {
        WorkerMonitoringService.isVideoStreamingFromUi = cameraBroadcasting
        WorkerMonitoringService.activeCameraFacingForService = activeFacing
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "LIVE TELEMETRY STREAM",
                style = MaterialTheme.typography.labelMedium,
                color = Cyan400
            )
            Text(
                text = "Camera & Microphone Broadcaster",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Videocam,
                                contentDescription = null,
                                tint = Emerald400
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Live Camera Broadcast", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Lens: ${activeFacing.name} (Admin synced)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = cameraBroadcasting,
                            enabled = localPerms.cameraGranted && worker != null,
                            onCheckedChange = { enabled ->
                                cameraBroadcasting = enabled
                                onToggleCameraBroadcast(enabled, activeFacing)
                            },
                            modifier = Modifier.testTag("camera_broadcast_switch")
                        )
                    }

                    if (localPerms.cameraGranted) {
                        CameraXPreviewBroadcaster(
                            cameraFacing = activeFacing,
                            streamingEnabled = cameraBroadcasting,
                            onFrameCaptured = { base64 ->
                                onPublishFrame(
                                    base64,
                                    activeFacing,
                                    serviceTelemetry.audioLevelDb,
                                    serviceTelemetry.audioBars,
                                    serviceTelemetry.micCaptureActive
                                )
                            }
                        )
                        OutlinedButton(
                            onClick = {
                                val flipped = if (activeFacing == CameraFacing.BACK) CameraFacing.FRONT else CameraFacing.BACK
                                activeFacing = flipped
                                onToggleCameraBroadcast(cameraBroadcasting, flipped)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("worker_flip_camera_button")
                        ) {
                            Icon(Icons.Filled.Cameraswitch, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Flip Camera (${activeFacing.name})")
                        }
                    } else {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Grant Camera permission on the Field Desk tab to enable live video streaming.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Mic, contentDescription = null, tint = Cyan400)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Microphone Audio Stream", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = if (serviceTelemetry.micCaptureActive) "Transmitting live audio" else "Mic standby",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = serviceTelemetry.micCaptureActive,
                            enabled = localPerms.micGranted && serviceTelemetry.running,
                            onCheckedChange = { enabled ->
                                WorkerMonitoringService.setMicEnabled(context, enabled)
                                onToggleMicBroadcast(enabled)
                            },
                            modifier = Modifier.testTag("mic_broadcast_switch")
                        )
                    }
                    LiveAudioVisualizer(
                        audioLevelDb = serviceTelemetry.audioLevelDb,
                        audioBars = serviceTelemetry.audioBars,
                        streamingAudio = serviceTelemetry.micCaptureActive,
                        mutedLocally = false,
                        onToggleMute = {}
                    )
                }
            }
        }
    }
}

@Composable
fun WorkerProofLogsScreen(
    mediaLogsState: UiState<List<MediaLog>>,
    modifier: Modifier = Modifier
) {
    val logs = (mediaLogsState as? UiState.Success)?.data.orEmpty()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "AUDIT & PROOF",
                style = MaterialTheme.typography.labelMedium,
                color = Emerald400
            )
            Text(
                text = "Work Proof Logs (${logs.size})",
                style = MaterialTheme.typography.headlineMedium
            )
        }
        if (logs.isEmpty()) {
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No work proof logs submitted yet. Log field photos or job notes from Field Desk.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(logs, key = { it.id }) { log ->
                MediaLogCard(log = log)
            }
        }
    }
}

@Composable
private fun WorkerRegistrationCard(
    defaultName: String,
    defaultPhone: String = "",
    defaultDept: String = "Field Inspection",
    onSubmit: (String, String, String) -> Unit
) {
    var name by remember(defaultName) { mutableStateOf(defaultName) }
    var phone by remember(defaultPhone) { mutableStateOf(defaultPhone) }
    var department by remember(defaultDept) { mutableStateOf(defaultDept.ifBlank { "Field Inspection" }) }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Complete Worker Registration",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Register your field profile to unlock permission onboarding, task dispatch, and live monitoring.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("worker_name_input")
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone Number") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("worker_phone_input")
            )
            OutlinedTextField(
                value = department,
                onValueChange = { department = it },
                label = { Text("Department / Trade") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("worker_dept_input")
            )
            Button(
                onClick = { onSubmit(name, phone, department) },
                enabled = name.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("register_worker_button")
            ) {
                Text("Register & Sync Profile")
            }
        }
    }
}

@Composable
private fun PermissionOnboardingCard(
    localPerms: LocalPermissionState,
    syncedStatus: String,
    onAllowAllClick: () -> Unit,
    onRequestBackgroundLocation: () -> Unit
) {
    val allCoreGranted = localPerms.locationGranted && localPerms.cameraGranted && localPerms.micGranted
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        tint = if (allCoreGranted) Emerald400 else Amber400
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Mandatory Sensor Permissions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            PermissionStatusBadges(
                locationGranted = localPerms.locationGranted,
                backgroundLocationGranted = localPerms.backgroundLocationGranted,
                cameraGranted = localPerms.cameraGranted,
                micGranted = localPerms.micGranted,
                permissionStatus = syncedStatus
            )

            Text(
                text = if (syncedStatus == PermissionSyncStatus.ALL_GRANTED.name) {
                    "All core monitoring permissions granted and synced in real time to Admin Live Monitor."
                } else {
                    "Grant Location, Camera, and Microphone access so Admin Live Monitor sees your ALL_GRANTED status immediately."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!allCoreGranted) {
                Button(
                    onClick = onAllowAllClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("allow_all_permissions_button")
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Allow All Permissions")
                }
            } else if (!localPerms.backgroundLocationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                OutlinedButton(
                    onClick = onRequestBackgroundLocation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("allow_background_location_button")
                ) {
                    Text("Enable Background Location (Allow All Time)")
                }
            }
        }
    }
}

@Composable
private fun WorkerTaskActionCard(
    task: FieldTask,
    onStartJob: () -> Unit,
    onCompleteJob: () -> Unit,
    onLogProof: () -> Unit
) {
    val status = TaskStatus.fromRaw(task.status)
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (status) {
                        TaskStatus.COMPLETED -> Emerald500.copy(alpha = 0.2f)
                        TaskStatus.IN_PROGRESS -> Cyan400.copy(alpha = 0.2f)
                        TaskStatus.PENDING -> Amber400.copy(alpha = 0.2f)
                    }
                ) {
                    Text(
                        text = task.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (status) {
                            TaskStatus.COMPLETED -> Emerald400
                            TaskStatus.IN_PROGRESS -> Cyan400
                            TaskStatus.PENDING -> Amber400
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (task.description.isNotBlank()) {
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (status != TaskStatus.COMPLETED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (status == TaskStatus.PENDING) {
                        Button(
                            onClick = onStartJob,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("start_task_${task.id}")
                        ) {
                            Text("Start Job")
                        }
                    } else {
                        Button(
                            onClick = onCompleteJob,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("complete_task_${task.id}")
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Complete")
                        }
                    }
                    OutlinedButton(
                        onClick = onLogProof,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("log_proof_task_${task.id}")
                    ) {
                        Text("Add Proof")
                    }
                }
            }
        }
    }
}

@Composable
private fun AddProofLogDialog(
    task: FieldTask?,
    onDismiss: () -> Unit,
    onSubmit: (String, MediaLogType, String?) -> Unit
) {
    val context = LocalContext.current
    var note by remember { mutableStateOf("") }
    var encodedThumb by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            encodedThumb = encodeUriThumbnailBase64(context, uri)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (task != null) "Log Proof: ${task.title}" else "Submit Work Proof Log") },
        text = {
            Column(
                modifier = Modifier.widthIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Field Observation / Proof Note") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("proof_note_input")
                )
                OutlinedButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Filled.AddAPhoto, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (encodedThumb == null) "Attach Site Photo (Optional)" else "Change Site Photo")
                }
                val bmp = remember(encodedThumb) { decodeBase64Bitmap(encodedThumb) }
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = "Selected site photo preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (note.isNotBlank()) {
                        val type = if (encodedThumb != null) MediaLogType.PHOTO else MediaLogType.TEXT
                        onSubmit(note, type, encodedThumb)
                    }
                },
                enabled = note.isNotBlank(),
                modifier = Modifier.testTag("submit_proof_log_button")
            ) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun encodeUriThumbnailBase64(context: Context, uri: Uri): String? {
    return try {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        val bitmap = input.use { BitmapFactory.decodeStream(it) } ?: return null
        val targetW = 360
        val targetH = ((bitmap.height.toFloat() / bitmap.width.toFloat()) * targetW).toInt().coerceIn(140, 480)
        val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 65, out)
        val bytes = out.toByteArray()
        if (bytes.size > 110_000) return null
        Base64.encodeToString(bytes, Base64.NO_WRAP)
    } catch (_: Exception) {
        null
    }
}
