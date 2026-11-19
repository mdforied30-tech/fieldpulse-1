package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppMode
import com.example.data.model.CameraFacing
import com.example.data.model.FieldTask
import com.example.data.model.LocationLog
import com.example.data.model.MediaLog
import com.example.data.model.MediaLogType
import com.example.data.model.TaskPriority
import com.example.data.model.TaskStatus
import com.example.data.model.UiState
import com.example.data.model.WorkerProfile
import com.example.data.model.WorkerTelemetry
import com.example.data.repository.FieldPulseRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: FieldPulseRepository,
    val currentUserId: String,
    val userEmail: String,
    val userDisplayName: String,
    val isAdminAccount: Boolean,
    initialMode: AppMode = if (isAdminAccount) AppMode.ADMIN else AppMode.WORKER
) : ViewModel() {

    private val _appMode = MutableStateFlow(
        if (initialMode == AppMode.ADMIN && isAdminAccount) AppMode.ADMIN else AppMode.WORKER
    )
    val appMode: StateFlow<AppMode> = _appMode.asStateFlow()

    private val _selectedWorkerId = MutableStateFlow<String?>(null)
    val selectedWorkerId: StateFlow<String?> = _selectedWorkerId.asStateFlow()

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvents = _toastEvents.asSharedFlow()

    private fun <T> Flow<T>.toUiState(label: String): StateFlow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing $label", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to observe $label"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val myWorkerProfile: StateFlow<UiState<WorkerProfile?>> =
        repository.observeWorker(currentUserId).toUiState("myWorkerProfile")

    val myTelemetry: StateFlow<UiState<WorkerTelemetry?>> =
        repository.observeWorkerTelemetry(currentUserId).toUiState("myTelemetry")

    val myTasks: StateFlow<UiState<List<FieldTask>>> =
        repository.observeWorkerTasks(currentUserId).toUiState("myTasks")

    val myMediaLogs: StateFlow<UiState<List<MediaLog>>> =
        repository.observeMediaLogs(currentUserId).toUiState("myMediaLogs")

    val allWorkers: StateFlow<UiState<List<WorkerProfile>>> =
        (if (isAdminAccount) repository.observeAllWorkers() else flowOf(emptyList()))
            .toUiState("allWorkers")

    val allTasks: StateFlow<UiState<List<FieldTask>>> =
        (if (isAdminAccount) repository.observeAllTasks() else flowOf(emptyList()))
            .toUiState("allTasks")

    val selectedWorkerProfile: StateFlow<UiState<WorkerProfile?>> =
        _selectedWorkerId.flatMapLatest { workerId ->
            if (workerId.isNullOrBlank()) flowOf(null)
            else repository.observeWorker(workerId)
        }.toUiState("selectedWorkerProfile")

    val selectedWorkerTelemetry: StateFlow<UiState<WorkerTelemetry?>> =
        _selectedWorkerId.flatMapLatest { workerId ->
            if (workerId.isNullOrBlank()) flowOf(null)
            else repository.observeWorkerTelemetry(workerId)
        }.toUiState("selectedWorkerTelemetry")

    val selectedWorkerLocationLogs: StateFlow<UiState<List<LocationLog>>> =
        _selectedWorkerId.flatMapLatest { workerId ->
            if (workerId.isNullOrBlank()) flowOf(emptyList())
            else repository.observeLocationLogs(workerId)
        }.toUiState("selectedWorkerLocationLogs")

    val selectedWorkerMediaLogs: StateFlow<UiState<List<MediaLog>>> =
        _selectedWorkerId.flatMapLatest { workerId ->
            if (workerId.isNullOrBlank()) flowOf(emptyList())
            else repository.observeMediaLogs(workerId)
        }.toUiState("selectedWorkerMediaLogs")

    fun switchMode(mode: AppMode) {
        if (mode == AppMode.ADMIN && !isAdminAccount) return
        _appMode.value = mode
    }

    fun selectWorkerForLiveMonitor(workerId: String?) {
        _selectedWorkerId.value = workerId
    }

    fun registerOrUpdateWorker(
        name: String,
        phone: String,
        department: String,
        locationGranted: Boolean,
        backgroundLocationGranted: Boolean,
        cameraGranted: Boolean,
        micGranted: Boolean
    ) {
        viewModelScope.launch {
            val res = repository.upsertWorkerRegistration(
                name = name,
                phone = phone,
                department = department,
                locationGranted = locationGranted,
                backgroundLocationGranted = backgroundLocationGranted,
                cameraGranted = cameraGranted,
                micGranted = micGranted
            )
            res.onSuccess {
                _toastEvents.tryEmit("Worker profile synced with Command Center")
            }.onFailure { err ->
                _toastEvents.tryEmit(err.localizedMessage ?: "Failed to save worker profile")
            }
        }
    }

    fun syncPermissions(
        locationGranted: Boolean,
        backgroundLocationGranted: Boolean,
        cameraGranted: Boolean,
        micGranted: Boolean
    ) {
        viewModelScope.launch {
            val res = repository.syncPermissions(
                workerId = currentUserId,
                locationGranted = locationGranted,
                backgroundLocationGranted = backgroundLocationGranted,
                cameraGranted = cameraGranted,
                micGranted = micGranted
            )
            res.onSuccess { status ->
                if (status.name == "ALL_GRANTED") {
                    _toastEvents.tryEmit("ALL_GRANTED synced to Admin Live Monitor")
                }
            }
        }
    }

    fun toggleWorkerCameraStream(enabled: Boolean, facing: CameraFacing) {
        viewModelScope.launch {
            repository.updateCameraStreamToggle(currentUserId, enabled, facing)
        }
    }

    fun toggleWorkerMicStream(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateMicStreamToggle(currentUserId, enabled)
        }
    }

    fun publishCameraFrame(
        frameBase64: String,
        facing: CameraFacing,
        audioDb: Double,
        audioBars: List<Double>,
        streamingAudio: Boolean
    ) {
        viewModelScope.launch {
            repository.updateTelemetry(
                workerId = currentUserId,
                cameraFrameBase64 = frameBase64,
                cameraFacing = facing,
                audioLevelDb = audioDb,
                audioBars = audioBars,
                streamingVideo = true,
                streamingAudio = streamingAudio
            )
        }
    }

    fun adminSwitchWorkerCamera(workerId: String, facing: CameraFacing) {
        viewModelScope.launch {
            val res = repository.requestWorkerCameraFacing(workerId, facing)
            res.onSuccess {
                _toastEvents.tryEmit("Requested ${facing.name} camera switch")
            }.onFailure { err ->
                _toastEvents.tryEmit(err.localizedMessage ?: "Camera switch failed")
            }
        }
    }

    fun createTask(
        title: String,
        description: String,
        worker: WorkerProfile,
        priority: TaskPriority,
        locationLabel: String
    ) {
        viewModelScope.launch {
            val res = repository.createTask(title, description, worker, priority, locationLabel)
            res.onSuccess {
                _toastEvents.tryEmit("Task assigned to ${worker.name}")
            }.onFailure { err ->
                _toastEvents.tryEmit(err.localizedMessage ?: "Failed to create task")
            }
        }
    }

    fun updateTaskStatus(taskId: String, newStatus: TaskStatus, workerNotes: String) {
        viewModelScope.launch {
            val res = repository.updateTaskStatus(taskId, newStatus, workerNotes)
            res.onSuccess {
                _toastEvents.tryEmit("Task status updated to ${newStatus.name}")
            }.onFailure { err ->
                _toastEvents.tryEmit(err.localizedMessage ?: "Failed to update task")
            }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            val res = repository.deleteTask(taskId)
            res.onSuccess {
                _toastEvents.tryEmit("Task deleted")
            }.onFailure { err ->
                _toastEvents.tryEmit(err.localizedMessage ?: "Failed to delete task")
            }
        }
    }

    fun addWorkProofLog(
        taskId: String?,
        note: String,
        mediaType: MediaLogType,
        thumbnailBase64: String?,
        latitude: Double?,
        longitude: Double?
    ) {
        viewModelScope.launch {
            val res = repository.addMediaLog(
                workerId = currentUserId,
                taskId = taskId,
                note = note,
                mediaType = mediaType,
                thumbnailBase64 = thumbnailBase64,
                latitude = latitude,
                longitude = longitude
            )
            res.onSuccess {
                _toastEvents.tryEmit("Work proof log uploaded")
            }.onFailure { err ->
                _toastEvents.tryEmit(err.localizedMessage ?: "Failed to upload proof log")
            }
        }
    }

    private companion object {
        const val TAG = "MainViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
