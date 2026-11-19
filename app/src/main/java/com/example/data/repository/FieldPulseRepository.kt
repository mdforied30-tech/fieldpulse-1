package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.CameraFacing
import com.example.data.model.FieldTask
import com.example.data.model.LocationLog
import com.example.data.model.MediaLog
import com.example.data.model.MediaLogType
import com.example.data.model.PermissionSyncStatus
import com.example.data.model.SUPER_ADMIN_EMAIL
import com.example.data.model.TaskPriority
import com.example.data.model.TaskStatus
import com.example.data.model.WorkerProfile
import com.example.data.model.WorkerTelemetry
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

class FieldPulseRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun isCurrentUserAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        return user.email.equals(SUPER_ADMIN_EMAIL, ignoreCase = true) && user.isEmailVerified
    }

    private fun <T> Flow<T>.wrapFirestoreErrors(
        operationType: OperationType,
        path: String,
        uid: String
    ): Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
        throw error
    }

    fun observeAllWorkers(): Flow<List<WorkerProfile>> = flow {
        val uid = requireUserId()
        val path = "workers"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        WorkerProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: Timestamp(0, 0) }
                }
                .wrapFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeWorker(workerId: String): Flow<WorkerProfile?> = flow {
        val uid = requireUserId()
        val path = "workers/$workerId"
        emitAll(
            db.collection("workers").document(workerId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            WorkerProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .wrapFirestoreErrors(OperationType.GET, path, uid)
        )
    }

    fun observeWorkerTelemetry(workerId: String): Flow<WorkerTelemetry?> = flow {
        val uid = requireUserId()
        val path = "workers/$workerId/telemetry/live"
        emitAll(
            db.collection("workers").document(workerId)
                .collection("telemetry").document("live")
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            WorkerTelemetry::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .wrapFirestoreErrors(OperationType.GET, path, uid)
        )
    }

    fun observeLocationLogs(workerId: String): Flow<List<LocationLog>> = flow {
        val uid = requireUserId()
        val path = "workers/$workerId/locationLogs"
        emitAll(
            db.collection("workers").document(workerId)
                .collection("locationLogs")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(25)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        LocationLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .wrapFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeMediaLogs(workerId: String): Flow<List<MediaLog>> = flow {
        val uid = requireUserId()
        val path = "workers/$workerId/mediaLogs"
        emitAll(
            db.collection("workers").document(workerId)
                .collection("mediaLogs")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(20)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        MediaLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .wrapFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeAllTasks(): Flow<List<FieldTask>> = flow {
        val uid = requireUserId()
        val path = "tasks"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        FieldTask::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .wrapFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeWorkerTasks(workerId: String): Flow<List<FieldTask>> = flow {
        val uid = requireUserId()
        val path = "tasks"
        emitAll(
            db.collection(path)
                .whereEqualTo("assignedWorkerId", workerId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        FieldTask::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .wrapFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun upsertWorkerRegistration(
        name: String,
        phone: String,
        department: String,
        locationGranted: Boolean,
        backgroundLocationGranted: Boolean,
        cameraGranted: Boolean,
        micGranted: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not authenticated"))
        val email = auth.currentUser?.email ?: "worker@fieldpulse.app"
        val docPath = "workers/$uid"
        return try {
            val cleanName = name.trim().take(80)
            val cleanPhone = phone.trim().take(30)
            val cleanDept = department.trim().ifBlank { "Field Operations" }.take(50)
            require(cleanName.isNotEmpty()) { "Name is required" }

            val status = computePermissionStatus(locationGranted, cameraGranted, micGranted)
            val docRef = db.collection("workers").document(uid)
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "name" to cleanName,
                        "phone" to cleanPhone,
                        "department" to cleanDept,
                        "locationGranted" to locationGranted,
                        "backgroundLocationGranted" to backgroundLocationGranted,
                        "cameraGranted" to cameraGranted,
                        "micGranted" to micGranted,
                        "permissionStatus" to status.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val role = if (isCurrentUserAdmin()) "admin" else "worker"
                val payload = mapOf(
                    "userId" to uid,
                    "name" to cleanName,
                    "email" to email.take(120),
                    "phone" to cleanPhone,
                    "department" to cleanDept,
                    "role" to role,
                    "locationGranted" to locationGranted,
                    "backgroundLocationGranted" to backgroundLocationGranted,
                    "cameraGranted" to cameraGranted,
                    "micGranted" to micGranted,
                    "permissionStatus" to status.name,
                    "serviceRunning" to false,
                    "activeCameraFacing" to CameraFacing.BACK.name,
                    "requestedCameraFacing" to CameraFacing.BACK.name,
                    "cameraStreamEnabled" to false,
                    "micStreamEnabled" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun syncPermissions(
        workerId: String,
        locationGranted: Boolean,
        backgroundLocationGranted: Boolean,
        cameraGranted: Boolean,
        micGranted: Boolean
    ): Result<PermissionSyncStatus> {
        val docPath = "workers/$workerId"
        return try {
            val uid = requireUserId()
            check(uid == workerId) { "Cannot sync permissions for another worker" }
            val status = computePermissionStatus(locationGranted, cameraGranted, micGranted)
            val docRef = db.collection("workers").document(workerId)
            if (docRef.get().await().exists()) {
                docRef.update(
                    mapOf(
                        "locationGranted" to locationGranted,
                        "backgroundLocationGranted" to backgroundLocationGranted,
                        "cameraGranted" to cameraGranted,
                        "micGranted" to micGranted,
                        "permissionStatus" to status.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(status)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateServiceState(workerId: String, running: Boolean): Result<Unit> {
        val docPath = "workers/$workerId"
        return try {
            val uid = requireUserId()
            check(uid == workerId)
            val docRef = db.collection("workers").document(workerId)
            if (docRef.get().await().exists()) {
                docRef.update(
                    mapOf(
                        "serviceRunning" to running,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateLocation(
        workerId: String,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double,
        speedMps: Double,
        appendLog: Boolean
    ): Result<Unit> {
        val docPath = "workers/$workerId"
        return try {
            val uid = requireUserId()
            check(uid == workerId)
            val docRef = db.collection("workers").document(workerId)
            if (docRef.get().await().exists()) {
                docRef.update(
                    mapOf(
                        "latitude" to latitude,
                        "longitude" to longitude,
                        "accuracyMeters" to accuracyMeters,
                        "speedMps" to speedMps,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            if (appendLog) {
                val logRef = docRef.collection("locationLogs").document()
                logRef.set(
                    mapOf(
                        "userId" to workerId,
                        "latitude" to latitude,
                        "longitude" to longitude,
                        "accuracyMeters" to accuracyMeters,
                        "speedMps" to speedMps,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateCameraStreamToggle(
        workerId: String,
        enabled: Boolean,
        activeFacing: CameraFacing
    ): Result<Unit> {
        val docPath = "workers/$workerId"
        return try {
            val uid = requireUserId()
            check(uid == workerId)
            db.collection("workers").document(workerId).update(
                mapOf(
                    "cameraStreamEnabled" to enabled,
                    "activeCameraFacing" to activeFacing.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateMicStreamToggle(workerId: String, enabled: Boolean): Result<Unit> {
        val docPath = "workers/$workerId"
        return try {
            val uid = requireUserId()
            check(uid == workerId)
            db.collection("workers").document(workerId).update(
                mapOf(
                    "micStreamEnabled" to enabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun requestWorkerCameraFacing(workerId: String, facing: CameraFacing): Result<Unit> {
        val docPath = "workers/$workerId"
        return try {
            requireUserId()
            db.collection("workers").document(workerId).update(
                mapOf(
                    "requestedCameraFacing" to facing.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateTelemetry(
        workerId: String,
        cameraFrameBase64: String?,
        cameraFacing: CameraFacing,
        audioLevelDb: Double,
        audioBars: List<Double>,
        streamingVideo: Boolean,
        streamingAudio: Boolean
    ): Result<Unit> {
        val docPath = "workers/$workerId/telemetry/live"
        return try {
            val uid = requireUserId()
            check(uid == workerId)
            val safeBars = audioBars.take(16).map { it.coerceIn(0.02, 1.0) }
            val safeFrame = cameraFrameBase64?.takeIf { it.isNotEmpty() && it.length <= 150_000 }
            val docRef = db.collection("workers").document(workerId)
                .collection("telemetry").document("live")

            val updatePayload = mutableMapOf<String, Any>(
                "cameraFacing" to cameraFacing.name,
                "audioLevelDb" to audioLevelDb,
                "audioBars" to safeBars,
                "streamingVideo" to streamingVideo,
                "streamingAudio" to streamingAudio,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (safeFrame != null) {
                updatePayload["cameraFrameBase64"] = safeFrame
            }

            try {
                docRef.update(updatePayload).await()
            } catch (e: FirebaseFirestoreException) {
                if (e.code == FirebaseFirestoreException.Code.NOT_FOUND) {
                    val createPayload = updatePayload.toMutableMap().apply {
                        put("userId", workerId)
                    }
                    docRef.set(createPayload).await()
                } else {
                    throw e
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addMediaLog(
        workerId: String,
        taskId: String?,
        note: String,
        mediaType: MediaLogType,
        thumbnailBase64: String?,
        latitude: Double?,
        longitude: Double?
    ): Result<Unit> {
        val docPath = "workers/$workerId/mediaLogs"
        return try {
            val uid = requireUserId()
            check(uid == workerId)
            val cleanNote = note.trim().take(500)
            require(cleanNote.isNotEmpty()) { "Work log note cannot be empty" }
            val docRef = db.collection("workers").document(workerId)
                .collection("mediaLogs").document()

            val payload = mutableMapOf<String, Any?>(
                "userId" to workerId,
                "taskId" to taskId?.takeIf { it.isNotBlank() }?.take(128),
                "note" to cleanNote,
                "mediaType" to mediaType.name,
                "thumbnailBase64" to thumbnailBase64?.takeIf { it.isNotBlank() && it.length <= 150_000 },
                "latitude" to latitude,
                "longitude" to longitude,
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createTask(
        title: String,
        description: String,
        worker: WorkerProfile,
        priority: TaskPriority,
        locationLabel: String
    ): Result<Unit> {
        val docPath = "tasks"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim().take(120)
            val cleanDesc = description.trim().take(1000)
            val cleanLoc = locationLabel.trim().take(120)
            require(cleanTitle.isNotEmpty()) { "Task title is required" }

            val docRef = db.collection("tasks").document()
            docRef.set(
                mapOf(
                    "title" to cleanTitle,
                    "description" to cleanDesc,
                    "assignedWorkerId" to worker.userId,
                    "assignedWorkerName" to worker.name.ifBlank { worker.email }.take(80),
                    "createdBy" to uid,
                    "priority" to priority.name,
                    "status" to TaskStatus.PENDING.name,
                    "locationLabel" to cleanLoc,
                    "workerNotes" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateTaskStatus(
        taskId: String,
        newStatus: TaskStatus,
        workerNotes: String
    ): Result<Unit> {
        val docPath = "tasks/$taskId"
        return try {
            requireUserId()
            require(taskId.isNotBlank())
            db.collection("tasks").document(taskId).update(
                mapOf(
                    "status" to newStatus.name,
                    "workerNotes" to workerNotes.trim().take(500),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteTask(taskId: String): Result<Unit> {
        val docPath = "tasks/$taskId"
        return try {
            requireUserId()
            require(taskId.isNotBlank())
            db.collection("tasks").document(taskId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    companion object {
        fun computePermissionStatus(
            locationGranted: Boolean,
            cameraGranted: Boolean,
            micGranted: Boolean
        ): PermissionSyncStatus = when {
            locationGranted && cameraGranted && micGranted -> PermissionSyncStatus.ALL_GRANTED
            locationGranted || cameraGranted || micGranted -> PermissionSyncStatus.PARTIAL
            else -> PermissionSyncStatus.PENDING
        }
    }
}
