package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

const val SUPER_ADMIN_EMAIL = "mdforied30@gmail.com"

enum class AppMode {
    ADMIN,
    WORKER
}

enum class PermissionSyncStatus {
    PENDING,
    PARTIAL,
    ALL_GRANTED;

    companion object {
        fun fromRaw(raw: String?): PermissionSyncStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

enum class CameraFacing {
    BACK,
    FRONT;

    companion object {
        fun fromRaw(raw: String?): CameraFacing =
            entries.firstOrNull { it.name == raw } ?: BACK
    }
}

enum class TaskPriority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT;

    companion object {
        fun fromRaw(raw: String?): TaskPriority =
            entries.firstOrNull { it.name == raw } ?: MEDIUM
    }
}

enum class TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED;

    companion object {
        fun fromRaw(raw: String?): TaskStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

enum class MediaLogType {
    TEXT,
    PHOTO,
    AUDIO;

    companion object {
        fun fromRaw(raw: String?): MediaLogType =
            entries.firstOrNull { it.name == raw } ?: TEXT
    }
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class WorkerProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val department: String = "Field Operations",
    val role: String = "worker",
    val locationGranted: Boolean = false,
    val backgroundLocationGranted: Boolean = false,
    val cameraGranted: Boolean = false,
    val micGranted: Boolean = false,
    val permissionStatus: String = PermissionSyncStatus.PENDING.name,
    val serviceRunning: Boolean = false,
    val activeCameraFacing: String = CameraFacing.BACK.name,
    val requestedCameraFacing: String = CameraFacing.BACK.name,
    val cameraStreamEnabled: Boolean = false,
    val micStreamEnabled: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Double? = null,
    val speedMps: Double? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class WorkerTelemetry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val cameraFrameBase64: String = "",
    val cameraFacing: String = CameraFacing.BACK.name,
    val audioLevelDb: Double = -60.0,
    val audioBars: List<Double> = List(16) { 0.08 },
    val audioChunkBase64: String = "",
    val streamingVideo: Boolean = false,
    val streamingAudio: Boolean = false,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class LocationLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracyMeters: Double = 0.0,
    val speedMps: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MediaLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val taskId: String = "",
    val note: String = "",
    val mediaType: String = MediaLogType.TEXT.name,
    val thumbnailBase64: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FieldTask(
    @DocumentId val id: String = "",
    val title: String = "",
    val description: String = "",
    val assignedWorkerId: String = "",
    val assignedWorkerName: String = "",
    val createdBy: String = "",
    val priority: String = TaskPriority.MEDIUM.name,
    val status: String = TaskStatus.PENDING.name,
    val locationLabel: String = "",
    val workerNotes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
