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
    
    val isRunning by SecurityCamService.isRunning.collectAsStateWithLifecycle()
    val captureCount by SecurityCamService.captureCount.collectAsStateWithLifecycle()
    
    var showPreview by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!permissionState.allPermissionsGranted) {
            permissionState.launchMultiplePermissionRequest()
        }
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
                    IconButton(onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                type = "image/*"
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(Intent.createChooser(intent, "View Photos"))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }) {
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
                    CameraPreview()
                }
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun CameraPreview() {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
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

