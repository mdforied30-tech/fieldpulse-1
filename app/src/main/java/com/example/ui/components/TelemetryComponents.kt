package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Base64
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.CameraFacing
import com.example.data.model.LocationLog
import com.example.data.model.PermissionSyncStatus
import com.example.ui.theme.Amber400
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Emerald500
import com.example.ui.theme.Navy850
import com.example.ui.theme.Obsidian950
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PermissionStatusBadges(
    locationGranted: Boolean,
    backgroundLocationGranted: Boolean,
    cameraGranted: Boolean,
    micGranted: Boolean,
    permissionStatus: String,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PermissionBadge(label = "GPS", granted = locationGranted)
        PermissionBadge(label = "BG GPS", granted = backgroundLocationGranted)
        PermissionBadge(label = "CAM", granted = cameraGranted)
        PermissionBadge(label = "MIC", granted = micGranted)
        if (permissionStatus == PermissionSyncStatus.ALL_GRANTED.name) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Emerald500.copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Emerald400)
            ) {
                Text(
                    text = "ALL_GRANTED",
                    style = MaterialTheme.typography.labelSmall,
                    color = Emerald400,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
fun PermissionBadge(label: String, granted: Boolean) {
    val bg = if (granted) Emerald500.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHighest
    val fg = if (granted) Emerald400 else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg
    ) {
        Text(
            text = if (granted) "● $label" else "○ $label",
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun LiveGpsRadarMap(
    latitude: Double?,
    longitude: Double?,
    accuracyMeters: Double?,
    speedMps: Double?,
    locationLogs: List<LocationLog>,
    workerName: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gps_pulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 12f,
        targetValue = 42f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_radius"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(Obsidian950)
                .border(1.dp, Cyan400.copy(alpha = 0.35f), MaterialTheme.shapes.medium)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val step = 36.dp.toPx()

                // Tactical coordinate grid
                var x = step
                while (x < w) {
                    drawLine(
                        color = Cyan400.copy(alpha = 0.10f),
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 1f
                    )
                    x += step
                }
                var y = step
                while (y < h) {
                    drawLine(
                        color = Cyan400.copy(alpha = 0.10f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f
                    )
                    y += step
                }

                // Radar concentric rings
                val center = Offset(w / 2f, h / 2f)
                listOf(0.22f, 0.42f, 0.65f).forEach { factor ->
                    drawCircle(
                        color = Emerald400.copy(alpha = 0.14f),
                        radius = min(w, h) * factor,
                        center = center,
                        style = Stroke(width = 1.2f)
                    )
                }

                if (latitude != null && longitude != null) {
                    val points = buildList {
                        locationLogs.reversed().forEach { add(it.latitude to it.longitude) }
                        add(latitude to longitude)
                    }
                    val minLat = points.minOf { it.first }
                    val maxLat = points.maxOf { it.first }
                    val minLon = points.minOf { it.second }
                    val maxLon = points.maxOf { it.second }
                    val latSpan = max(maxLat - minLat, 0.0008)
                    val lonSpan = max(maxLon - minLon, 0.0008)

                    val mapped = points.map { (pLat, pLon) ->
                        val nx = ((pLon - minLon) / lonSpan).toFloat()
                        val ny = 1f - ((pLat - minLat) / latSpan).toFloat()
                        Offset(
                            x = w * (0.18f + nx * 0.64f),
                            y = h * (0.20f + ny * 0.60f)
                        )
                    }

                    if (mapped.size > 1) {
                        val trail = Path().apply {
                            moveTo(mapped.first().x, mapped.first().y)
                            mapped.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(
                            path = trail,
                            color = Cyan400.copy(alpha = 0.8f),
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    val livePos = mapped.lastOrNull() ?: center
                    drawCircle(
                        color = Emerald400.copy(alpha = pulseAlpha),
                        radius = pulseRadius,
                        center = livePos
                    )
                    drawCircle(
                        color = Emerald500,
                        radius = 8.dp.toPx(),
                        center = livePos
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.dp.toPx(),
                        center = livePos
                    )
                }
            }

            // HUD Overlay top-left
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Navy850.copy(alpha = 0.88f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = "GPS Fix",
                        tint = if (latitude != null) Emerald400 else Amber400,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (latitude != null && longitude != null) {
                            String.format("%.5f, %.5f", latitude, longitude)
                        } else {
                            "AWAITING GPS TELEMETRY"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            }

            // Bottom telemetry metrics
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Navy850.copy(alpha = 0.88f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            ) {
                val acc = accuracyMeters?.let { String.format("±%.1fm", it) } ?: "--"
                val spd = speedMps?.let { String.format("%.1f m/s", it) } ?: "--"
                Text(
                    text = "$workerName • ACC $acc • SPD $spd • ${locationLogs.size} pts",
                    style = MaterialTheme.typography.labelSmall,
                    color = Cyan400,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
fun LiveCameraFeedPlayer(
    frameBase64: String?,
    cameraFacing: String,
    streamingVideo: Boolean,
    onSwitchCameraClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(frameBase64) {
        decodeBase64Bitmap(frameBase64)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(Obsidian950)
            .border(
                1.dp,
                if (streamingVideo) Emerald400.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f),
                MaterialTheme.shapes.medium
            ),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Worker live camera feed",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent, Color.Black.copy(alpha = 0.45f))
                        )
                    )
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (streamingVideo) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                    contentDescription = "Camera feed status",
                    tint = if (streamingVideo) Cyan400 else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = if (streamingVideo) "Waiting for next video frame..." else "Worker camera broadcast paused",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }
        }

        // Top badges
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (streamingVideo) Color(0xFFDC2626) else Navy850.copy(alpha = 0.9f)
            ) {
                Text(
                    text = if (streamingVideo) "● LIVE CAM ($cameraFacing)" else "CAM IDLE ($cameraFacing)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        if (onSwitchCameraClick != null) {
            FilledTonalButton(
                onClick = onSwitchCameraClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .testTag("switch_worker_camera_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Cameraswitch,
                    contentDescription = "Switch Front/Back Camera",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (cameraFacing == CameraFacing.BACK.name) "Switch to Front" else "Switch to Back",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
fun LiveAudioVisualizer(
    audioLevelDb: Double,
    audioBars: List<Double>,
    streamingAudio: Boolean,
    mutedLocally: Boolean,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = Obsidian950,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (streamingAudio && !mutedLocally) Emerald400.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.12f)
        ),
        modifier = modifier.fillMaxWidth()
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (streamingAudio) Icons.Filled.Mic else Icons.Filled.MicOff,
                        contentDescription = "Microphone status",
                        tint = if (streamingAudio) Emerald400 else Amber400,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (streamingAudio) "LIVE MICROPHONE STREAM" else "MICROPHONE STANDBY",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = String.format("Input Gain: %.1f dBFS", audioLevelDb),
                            style = MaterialTheme.typography.labelSmall,
                            color = Cyan400
                        )
                    }
                }
                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Navy850)
                        .testTag("toggle_audio_mute_button")
                ) {
                    Icon(
                        imageVector = if (mutedLocally) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                        contentDescription = if (mutedLocally) "Unmute Audio Monitor" else "Mute Audio Monitor",
                        tint = if (mutedLocally) Amber400 else Emerald400
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                val safeBars = if (audioBars.isEmpty()) List(16) { 0.08 } else audioBars.take(16)
                safeBars.forEach { level ->
                    val target = if (mutedLocally || !streamingAudio) 0.08f else level.toFloat().coerceIn(0.08f, 1.0f)
                    val animatedHeight by animateFloatAsState(
                        targetValue = target,
                        animationSpec = tween(140),
                        label = "bar_height"
                    )
                    val barColor = when {
                        mutedLocally || !streamingAudio -> Color.Gray.copy(alpha = 0.4f)
                        animatedHeight > 0.72f -> Amber400
                        else -> Emerald400
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height((44f * animatedHeight).dp)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(barColor)
                    )
                }
            }
        }
    }
}

@Composable
fun CameraXPreviewBroadcaster(
    cameraFacing: CameraFacing,
    streamingEnabled: Boolean,
    onFrameCaptured: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }

    DisposableEffect(cameraFacing, streamingEnabled, lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var provider: ProcessCameraProvider? = null

        cameraProviderFuture.addListener({
            try {
                provider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val selector = if (cameraFacing == CameraFacing.FRONT) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                var lastSentMs = 0L
                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    try {
                        val now = System.currentTimeMillis()
                        if (streamingEnabled && now - lastSentMs >= 1800L) {
                            val encoded = imageProxyToCompactBase64(imageProxy, cameraFacing == CameraFacing.FRONT)
                            if (encoded != null) {
                                lastSentMs = now
                                mainExecutor.execute { onFrameCaptured(encoded) }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("CameraX", "Frame encode error: ${e.message}")
                    } finally {
                        imageProxy.close()
                    }
                }

                provider?.unbindAll()
                provider?.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            } catch (e: Exception) {
                Log.w("CameraX", "Camera bind failed (emulator or missing lens): ${e.message}")
            }
        }, mainExecutor)

        onDispose {
            try {
                provider?.unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(Obsidian950)
    )
}

fun decodeBase64Bitmap(base64: String?): ImageBitmap? {
    if (base64.isNullOrBlank()) return null
    return try {
        val clean = base64.substringAfter("base64,", base64)
        val bytes = Base64.decode(clean, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

private fun imageProxyToCompactBase64(image: ImageProxy, mirrorHorizontal: Boolean): String? {
    if (image.format != ImageFormat.YUV_420_888) return null
    val yBuffer = image.planes[0].buffer
    val uBuffer = image.planes[1].buffer
    val vBuffer = image.planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
    val rawOut = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 65, rawOut)
    val jpegBytes = rawOut.toByteArray()
    val decoded = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size) ?: return null

    val matrix = Matrix().apply {
        postRotate(image.imageInfo.rotationDegrees.toFloat())
        if (mirrorHorizontal) postScale(-1f, 1f)
    }
    val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    val targetW = 320
    val targetH = ((rotated.height.toFloat() / rotated.width.toFloat()) * targetW).toInt().coerceIn(120, 400)
    val scaled = Bitmap.createScaledBitmap(rotated, targetW, targetH, true)

    val finalOut = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 55, finalOut)
    val bytes = finalOut.toByteArray()
    if (bytes.size > 90_000) return null
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}
