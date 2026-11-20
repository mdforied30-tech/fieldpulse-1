package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.CameraFacing
import com.example.data.repository.FieldPulseRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

data class ServiceTelemetrySnapshot(
    val running: Boolean = false,
    val uptimeSeconds: Long = 0L,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Double? = null,
    val speedMps: Double? = null,
    val audioLevelDb: Double = -60.0,
    val audioBars: List<Double> = List(16) { 0.08 },
    val micCaptureActive: Boolean = false
)

class WorkerMonitoringService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: FieldPulseRepository
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var locationCallback: LocationCallback? = null
    private var heartbeatJob: Job? = null
    private var audioJob: Job? = null
    private var lastLocationLogTimeMs = 0L

    override fun onCreate() {
        super.onCreate()
        repository = FieldPulseRepository(applicationContext)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTelemetry()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SET_MIC -> {
                val enabled = intent.getBooleanExtra(EXTRA_MIC_ENABLED, true)
                if (enabled) startAudioMonitor() else stopAudioMonitor()
                return START_STICKY
            }
            else -> {
                startForegroundCompat()
                startTelemetryLoops()
                return START_STICKY
            }
        }
    }

    private fun startForegroundCompat() {
        val notification = buildNotification("Active GPS & Telemetry Sync")
        val serviceType = resolveAllowedForegroundTypes()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Foreground service permission missing for type=$serviceType", e)
            stopSelf()
        }
    }

    private fun resolveAllowedForegroundTypes(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        var types = 0
        if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        ) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (hasPermission(Manifest.permission.CAMERA)) {
                types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
            if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
        }
        if (types == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            types = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        return types
    }

    private fun startTelemetryLoops() {
        val uid = Firebase.auth.currentUser?.uid
        _telemetryState.value = _telemetryState.value.copy(running = true)

        if (uid != null) {
            serviceScope.launch {
                repository.updateServiceState(uid, true)
            }
        }

        startLocationUpdates()
        if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
            startAudioMonitor()
        }

        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            var seconds = 0L
            while (isActive) {
                delay(1000L)
                seconds += 1L
                _telemetryState.value = _telemetryState.value.copy(uptimeSeconds = seconds)
                if (seconds % 10L == 0L) {
                    val locText = _telemetryState.value.let {
                        if (it.latitude != null && it.longitude != null) {
                            String.format("GPS %.4f, %.4f", it.latitude, it.longitude)
                        } else {
                            "Awaiting GPS fix"
                        }
                    }
                    val manager = getSystemService(NotificationManager::class.java)
                    manager?.notify(NOTIFICATION_ID, buildNotification("$locText • ${seconds}s live"))
                }
            }
        }
    }

    private fun pushLocationToCloud(loc: Location, forceLog: Boolean = false) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        val now = System.currentTimeMillis()
        val shouldLogHistory = forceLog || (now - lastLocationLogTimeMs >= 15_000L)
        if (shouldLogHistory) lastLocationLogTimeMs = now

        _telemetryState.value = _telemetryState.value.copy(
            latitude = loc.latitude,
            longitude = loc.longitude,
            accuracyMeters = loc.accuracy.toDouble(),
            speedMps = loc.speed.toDouble()
        )

        serviceScope.launch {
            repository.updateLocation(
                workerId = uid,
                latitude = loc.latitude,
                longitude = loc.longitude,
                accuracyMeters = loc.accuracy.toDouble(),
                speedMps = loc.speed.toDouble(),
                appendLog = shouldLogHistory
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val hasFine = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarse = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!hasFine && !hasCoarse) return

        // Immediate initial fix so Admin sees worker GPS right away even indoors/stationary
        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) pushLocationToCloud(loc, forceLog = true)
        }
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) pushLocationToCloud(loc, forceLog = false)
            }

        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000L)
            .setMinUpdateIntervalMillis(2500L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                pushLocationToCloud(loc)
            }
        }
        locationCallback = callback
        fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    @SuppressLint("MissingPermission")
    private fun startAudioMonitor() {
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) return
        if (audioJob?.isActive == true) return

        audioJob = serviceScope.launch {
            val sampleRate = 16000
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)

            var recorder: AudioRecord? = null
            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuffer
                )
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    Log.w(TAG, "AudioRecord failed to initialize")
                    return@launch
                }
                recorder.startRecording()
                _telemetryState.value = _telemetryState.value.copy(micCaptureActive = true)
                val uid = Firebase.auth.currentUser?.uid
                if (uid != null) {
                    repository.updateMicStreamToggle(uid, true)
                }

                val buffer = ShortArray(1024)
                val pcmAccumulator = ByteArrayOutputStream()
                var lastCloudSyncMs = 0L

                while (isActive) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var sumSquares = 0.0
                        for (i in 0 until read) {
                            val s = buffer[i].toDouble()
                            sumSquares += s * s
                            // Downsample 2:1 (16kHz -> 8kHz) little-endian PCM16 for compact cloud streaming
                            if (i % 2 == 0 && pcmAccumulator.size() < 20_000) {
                                val sample = buffer[i].toInt()
                                pcmAccumulator.write(sample and 0xFF)
                                pcmAccumulator.write((sample shr 8) and 0xFF)
                            }
                        }
                        val rms = sqrt(sumSquares / read)
                        val db = if (rms > 1.0) (20.0 * log10(rms / 32767.0)) else -60.0

                        val bars = List(16) { idx ->
                            val sliceStart = (idx * read) / 16
                            val sliceEnd = (((idx + 1) * read) / 16).coerceAtLeast(sliceStart + 1)
                            var peak = 0
                            for (j in sliceStart until sliceEnd) {
                                val v = abs(buffer[j].toInt())
                                if (v > peak) peak = v
                            }
                            ((peak / 12000.0).coerceIn(0.06, 1.0))
                        }

                        _telemetryState.value = _telemetryState.value.copy(
                            audioLevelDb = db,
                            audioBars = bars
                        )

                        val now = System.currentTimeMillis()
                        if (now - lastCloudSyncMs >= 1200L) {
                            lastCloudSyncMs = now
                            val pcmBytes = pcmAccumulator.toByteArray()
                            pcmAccumulator.reset()
                            val audioBase64 = if (pcmBytes.isNotEmpty()) {
                                Base64.encodeToString(pcmBytes, Base64.NO_WRAP)
                            } else null

                            val currentUid = Firebase.auth.currentUser?.uid
                            if (currentUid != null) {
                                repository.updateTelemetry(
                                    workerId = currentUid,
                                    cameraFrameBase64 = null,
                                    cameraFacing = activeCameraFacingForService,
                                    audioLevelDb = db,
                                    audioBars = bars,
                                    audioChunkBase64 = audioBase64,
                                    streamingVideo = isVideoStreamingFromUi,
                                    streamingAudio = true
                                )
                            }
                        }
                    }
                    delay(80L)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Audio monitor stopped: ${e.message}")
            } finally {
                _telemetryState.value = _telemetryState.value.copy(
                    micCaptureActive = false,
                    audioLevelDb = -60.0,
                    audioBars = List(16) { 0.06 }
                )
                try {
                    recorder?.stop()
                    recorder?.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun stopAudioMonitor() {
        audioJob?.cancel()
        audioJob = null
        val uid = Firebase.auth.currentUser?.uid
        if (uid != null) {
            serviceScope.launch {
                repository.updateMicStreamToggle(uid, false)
            }
        }
    }

    private fun stopTelemetry() {
        heartbeatJob?.cancel()
        audioJob?.cancel()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null
        _telemetryState.value = ServiceTelemetrySnapshot(running = false)

        val uid = Firebase.auth.currentUser?.uid
        if (uid != null) {
            CoroutineScope(Dispatchers.IO).launch {
                repository.updateServiceState(uid, false)
                repository.updateMicStreamToggle(uid, false)
            }
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.service_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent worker telemetry notification"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, WorkerMonitoringService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(0, "Stop Monitoring", stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
        stopTelemetry()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "WorkerMonitoringService"
        private const val CHANNEL_ID = "fieldpulse_monitoring_channel"
        private const val NOTIFICATION_ID = 4102
        const val ACTION_START = "com.example.action.START_MONITORING"
        const val ACTION_STOP = "com.example.action.STOP_MONITORING"
        const val ACTION_SET_MIC = "com.example.action.SET_MIC"
        const val EXTRA_MIC_ENABLED = "extra_mic_enabled"

        @Volatile
        var isVideoStreamingFromUi: Boolean = false

        @Volatile
        var activeCameraFacingForService: CameraFacing = CameraFacing.BACK

        private val _telemetryState = MutableStateFlow(ServiceTelemetrySnapshot())
        val telemetryState: StateFlow<ServiceTelemetrySnapshot> = _telemetryState.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, WorkerMonitoringService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, WorkerMonitoringService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun setMicEnabled(context: Context, enabled: Boolean) {
            val intent = Intent(context, WorkerMonitoringService::class.java).apply {
                action = ACTION_SET_MIC
                putExtra(EXTRA_MIC_ENABLED, enabled)
            }
            context.startService(intent)
        }
    }
}
