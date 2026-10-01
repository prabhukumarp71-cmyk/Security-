package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.camera.view.PreviewView
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import androidx.camera.camera2.interop.Camera2Interop
import android.hardware.camera2.CaptureRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var settingsRepo: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepo = SettingsRepository(this)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                SecurityCamApp(settingsRepo)
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SecurityCamApp(settingsRepo: SettingsRepository) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val permissionsToRequest = mutableListOf(Manifest.permission.CAMERA)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    
    val permissionState = rememberMultiplePermissionsState(permissionsToRequest)
    
    val isContinuousMode by settingsRepo.isContinuousMode.collectAsStateWithLifecycle(initialValue = true)
    val interval by settingsRepo.interval.collectAsStateWithLifecycle(initialValue = 5)
    val retentionDays by settingsRepo.retentionDays.collectAsStateWithLifecycle(initialValue = 7)
    val motionThreshold by settingsRepo.motionThreshold.collectAsStateWithLifecycle(initialValue = 20)
    val isEnhancedMode by settingsRepo.isEnhancedMode.collectAsStateWithLifecycle(initialValue = true)
    val isHdrMode by settingsRepo.isHdrMode.collectAsStateWithLifecycle(initialValue = true)
    val aspectRatio by settingsRepo.aspectRatio.collectAsStateWithLifecycle(initialValue = 0)
    val isFarOnlyMode by settingsRepo.isFarOnlyMode.collectAsStateWithLifecycle(initialValue = false)
    val farFocusLock by settingsRepo.farFocusLock.collectAsStateWithLifecycle(initialValue = true)
    val nearExclusionThreshold by settingsRepo.nearExclusionThreshold.collectAsStateWithLifecycle(initialValue = 25)
    val motoDetailBoost by settingsRepo.motoDetailBoost.collectAsStateWithLifecycle(initialValue = true)
    val sharpnessLevel by settingsRepo.sharpnessLevel.collectAsStateWithLifecycle(initialValue = 2)
    val softwareTextureBoost by settingsRepo.softwareTextureBoost.collectAsStateWithLifecycle(initialValue = true)
    val detailStrength by settingsRepo.detailStrength.collectAsStateWithLifecycle(initialValue = 60)
    
    val isRunning by SecurityCamService.isRunning.collectAsStateWithLifecycle()
    val captureCount by SecurityCamService.captureCount.collectAsStateWithLifecycle()
    val lastDetectionStatus by SecurityCamService.lastDetectionStatus.collectAsStateWithLifecycle()
    
    var showPreview by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!permissionState.allPermissionsGranted) {
            permissionState.launchMultiplePermissionRequest()
        }
    }

    var currentScreen by remember { mutableStateOf("home") }

    if (currentScreen == "gallery") {
        GalleryScreen(onBack = { currentScreen = "home" })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security Cam") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = { currentScreen = "gallery" }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!permissionState.allPermissionsGranted) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Permissions Required", style = MaterialTheme.typography.titleMedium)
                        Text("Please grant camera and notification permissions for the app to function.")
                        Button(
                            onClick = { permissionState.launchMultiplePermissionRequest() },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Grant Permissions")
                        }
                    }
                }
            }

            // Battery Optimization Warning
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Battery Optimization", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("For reliable background capture when the screen is off (especially on Motorola devices), disable battery optimization.", style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Disable Optimization")
                    }
                }
            }

            // Status & Controls
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Service Control", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Status: ${if (isRunning) "Running" else "Stopped"}")
                        Button(
                            onClick = {
                                if (isRunning) {
                                    context.startService(Intent(context, SecurityCamService::class.java).apply { action = SecurityCamService.ACTION_STOP })
                                } else {
                                    context.startForegroundService(Intent(context, SecurityCamService::class.java))
                                }
                            }
                        ) {
                            Icon(
                                if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (isRunning) "Stop" else "Start")
                        }
                    }
                    if (isRunning) {
                        Spacer(Modifier.height(8.dp))
                        Text("Session Captures: $captureCount", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        Text("Detection: $lastDetectionStatus", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Separate UI Option: Far-Distance Capture Feature
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isFarOnlyMode) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant
                ),
                border = if (isFarOnlyMode) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.CenterFocusStrong,
                                contentDescription = null,
                                tint = if (isFarOnlyMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Far-Distance Capture", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (isFarOnlyMode) "Captures far • Ignores near obstacles" else "Standard capture range",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isFarOnlyMode,
                            onCheckedChange = { coroutineScope.launch { settingsRepo.setFarOnlyMode(it) } }
                        )
                    }

                    if (isFarOnlyMode) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Intelligently filters out foreground disturbances (insects flying near lens, rain, leaves, hands) and only triggers on far distant activity while preserving full Motorola image processing.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Optical Far Lock (Infinity Focus)", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Locks lens to infinity (0.0 diopters). Keeps near objects blurred so they don't trigger.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Switch(
                                checked = farFocusLock,
                                onCheckedChange = { coroutineScope.launch { settingsRepo.setFarFocusLock(it) } }
                            )
                        }

                        Column {
                            Text(
                                "Near Obstacle Exclusion Size: $nearExclusionThreshold% of frame",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "Disturbances occupying more than $nearExclusionThreshold% are classified as near obstacles and ignored.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = nearExclusionThreshold.toFloat(),
                                onValueChange = { coroutineScope.launch { settingsRepo.setNearExclusionThreshold(it.toInt()) } },
                                valueRange = 10f..50f,
                                steps = 8
                            )
                        }

                        if (isRunning) {
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Live Status: $lastDetectionStatus",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Settings
            Text("Settings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Mode: ${if (isContinuousMode) "Continuous" else "Motion Detection"}")
                Switch(
                    checked = isContinuousMode,
                    onCheckedChange = { coroutineScope.launch { settingsRepo.setMode(it) } }
                )
            }

            if (isContinuousMode) {
                Column {
                    Text("Capture Interval: $interval seconds")
                    Slider(
                        value = interval.toFloat(),
                        onValueChange = { coroutineScope.launch { settingsRepo.setInterval(it.toInt()) } },
                        valueRange = 1f..60f,
                        steps = 59
                    )
                }
            } else {
                Column {
                    Text("Motion Sensitivity (lower is more sensitive): $motionThreshold")
                    Slider(
                        value = motionThreshold.toFloat(),
                        onValueChange = { coroutineScope.launch { settingsRepo.setMotionThreshold(it.toInt()) } },
                        valueRange = 5f..100f,
                        steps = 19
                    )
                }
            }

            Column {
                Text("Retention: $retentionDays days")
                Slider(
                    value = retentionDays.toFloat(),
                    onValueChange = { coroutineScope.launch { settingsRepo.setRetention(it.toInt()) } },
                    valueRange = 1f..30f,
                    steps = 29
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Image Style", style = MaterialTheme.typography.bodyLarge)
                    Text(if (isEnhancedMode) "Enhanced (Moto Processing)" else "Natural (No AI)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = isEnhancedMode,
                    onCheckedChange = { coroutineScope.launch { settingsRepo.setEnhancedMode(it) } }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("HDR Mode", style = MaterialTheme.typography.bodyLarge)
                    Text(if (isHdrMode) "High Dynamic Range ON" else "Standard Dynamic Range", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = isHdrMode,
                    onCheckedChange = { coroutineScope.launch { settingsRepo.setHdrMode(it) } }
                )
            }

            // Moto Sharpness & Detail Boost (Matches & Exceeds Moto Camera App)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (motoDetailBoost) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant
                ),
                border = if (motoDetailBoost) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
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
                            Text("Moto Detail & Sharpness Boost", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (motoDetailBoost) "Matches Moto Camera app: maximum edge sharpness, detail extraction & 100% JPEG" else "Standard camera sharpness",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = motoDetailBoost,
                            onCheckedChange = { coroutineScope.launch { settingsRepo.setMotoDetailBoost(it) } }
                        )
                    }

                    if (motoDetailBoost) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Text(
                            "Hardware Sharpness Profile",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = sharpnessLevel == 0,
                                onClick = { coroutineScope.launch { settingsRepo.setSharpnessLevel(0) } },
                                label = { Text("Soft") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = sharpnessLevel == 1,
                                onClick = { coroutineScope.launch { settingsRepo.setSharpnessLevel(1) } },
                                label = { Text("Balanced") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = sharpnessLevel == 2,
                                onClick = { coroutineScope.launch { settingsRepo.setSharpnessLevel(2) } },
                                label = { Text("Ultra Sharp") },
                                modifier = Modifier.weight(1.3f)
                            )
                            FilterChip(
                                selected = sharpnessLevel == 3,
                                onClick = { coroutineScope.launch { settingsRepo.setSharpnessLevel(3) } },
                                label = { Text("Extreme") },
                                modifier = Modifier.weight(1.1f)
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        // Micro-Texture & Unsharp Mask Detail Enhancer
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Micro-Detail & Texture Boost", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Applies high-frequency unsharp mask sharpening on distant text, faces, and foliage.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = softwareTextureBoost,
                                onCheckedChange = { coroutineScope.launch { settingsRepo.setSoftwareTextureBoost(it) } }
                            )
                        }

                        if (softwareTextureBoost) {
                            Column {
                                Text(
                                    "Detail Enhancement Intensity: $detailStrength%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Slider(
                                    value = detailStrength.toFloat(),
                                    onValueChange = { coroutineScope.launch { settingsRepo.setDetailStrength(it.toInt()) } },
                                    valueRange = 20f..100f,
                                    steps = 8
                                )
                            }
                        }
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Aspect Ratio", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = aspectRatio == 0,
                        onClick = { coroutineScope.launch { settingsRepo.setAspectRatio(0) } },
                        label = { Text("4:3") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = aspectRatio == 1,
                        onClick = { coroutineScope.launch { settingsRepo.setAspectRatio(1) } },
                        label = { Text("16:9") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Live Preview (while app is open)")
                Switch(
                    checked = showPreview,
                    onCheckedChange = { showPreview = it }
                )
            }

            if (showPreview) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f/3f)
                ) {
                    CameraPreview(
                        isFarOnlyMode = isFarOnlyMode,
                        farFocusLock = farFocusLock,
                        motoDetailBoost = motoDetailBoost,
                        sharpnessLevel = sharpnessLevel
                    )
                }
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun CameraPreview(
    isFarOnlyMode: Boolean = false,
    farFocusLock: Boolean = false,
    motoDetailBoost: Boolean = true,
    sharpnessLevel: Int = 2
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val previewBuilder = Preview.Builder()
                val previewExtender = Camera2Interop.Extender(previewBuilder)

                if (motoDetailBoost) {
                    try {
                        if (sharpnessLevel >= 2) {
                            previewExtender.setCaptureRequestOption(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
                        } else if (sharpnessLevel == 1) {
                            previewExtender.setCaptureRequestOption(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_FAST)
                        } else {
                            previewExtender.setCaptureRequestOption(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (isFarOnlyMode && farFocusLock) {
                    try {
                        previewExtender.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                        previewExtender.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val preview = previewBuilder.build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

