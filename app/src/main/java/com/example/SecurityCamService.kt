package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.camera2.interop.Camera2Interop
import android.hardware.camera2.CaptureRequest
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import android.content.ContentValues
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class SecurityCamService : Service(), LifecycleOwner {
    
    companion object {
        const val ACTION_STOP = "com.example.ACTION_STOP"
        const val CHANNEL_ID = "security_cam_channel"
        const val NOTIFICATION_ID = 1
        val isRunning = MutableStateFlow(false)
        val captureCount = MutableStateFlow(0)
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private lateinit var settingsRepo: SettingsRepository
    private var serviceJob: Job? = null
    private var captureJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var startTime = 0L

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        settingsRepo = SettingsRepository(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!isRunning.value) {
            isRunning.value = true
            captureCount.value = 0
            startTime = System.currentTimeMillis()
            
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            } else 0
            
            startForeground(NOTIFICATION_ID, createNotification(), type)
            
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            
            acquireWakeLock()
            startCameraPipeline()
            startNotificationUpdater()
            cleanUpOldPhotos()
        }

        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SecurityCam::CaptureWakelock").apply {
            acquire()
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, SecurityCamService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE)

        val elapsed = (System.currentTimeMillis() - startTime) / 1000
        val hours = elapsed / 3600
        val minutes = (elapsed % 3600) / 60
        val seconds = elapsed % 60
        val timeStr = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Security Camera Active")
            .setContentText("Captures: ${captureCount.value} | Uptime: $timeStr")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(mainPendingIntent)
            .addAction(0, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Security Camera Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun startNotificationUpdater() {
        CoroutineScope(Dispatchers.Main).launch {
            while (isRunning.value) {
                delay(1000)
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, createNotification())
            }
        }
    }

    private fun startCameraPipeline() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                serviceJob = CoroutineScope(Dispatchers.Main).launch {
                    bindCameraUseCases()
                }
            } catch (e: Exception) {
                Log.e("SecurityCam", "Camera initialization failed", e)
                CoroutineScope(Dispatchers.Main).launch {
                    delay(5000)
                    if (isRunning.value) startCameraPipeline()
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private suspend fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        provider.unbindAll()

        val isContinuous = settingsRepo.isContinuousMode.first()
        val intervalSeconds = settingsRepo.interval.first()
        val motionThreshold = settingsRepo.motionThreshold.first()

        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
            .build()

        val imageCaptureBuilder = ImageCapture.Builder()
            .setResolutionSelector(resolutionSelector)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)

        val ext = Camera2Interop.Extender(imageCaptureBuilder)
        ext.setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        ext.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        ext.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        ext.setCaptureRequestOption(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
        ext.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
        ext.setCaptureRequestOption(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_HIGH_QUALITY)
        ext.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_HIGH_QUALITY)
        ext.setCaptureRequestOption(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE)

        imageCapture = imageCaptureBuilder.build()

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        val useCases = mutableListOf<androidx.camera.core.UseCase>(imageCapture!!)

        val analysisBuilder = ImageAnalysis.Builder()
            .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER)).build())
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            
        val imageAnalysis = analysisBuilder.build()

        if (!isContinuous) {
            val analyzer = MotionAnalyzer(motionThreshold) { 
                takePhoto() 
            }
            imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), analyzer)
        }
        useCases.add(imageAnalysis)

        try {
            provider.bindToLifecycle(this, cameraSelector, *useCases.toTypedArray())
            
            if (isContinuous) {
                startContinuousCapture(intervalSeconds)
            }
        } catch (e: Exception) {
            Log.e("SecurityCam", "Use case binding failed", e)
        }
    }
    
    private fun startContinuousCapture(intervalSeconds: Int) {
        captureJob?.cancel()
        captureJob = CoroutineScope(Dispatchers.IO).launch {
            while (isRunning.value && isActive) {
                takePhoto()
                delay(intervalSeconds * 1000L)
            }
        }
    }

    private var lastCaptureTime = 0L
    private fun takePhoto() {
        if (!isRunning.value) return
        
        val now = System.currentTimeMillis()
        if (now - lastCaptureTime < 2000L) return
        
        val capture = imageCapture ?: return
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val timeStr = SimpleDateFormat("HH-mm-ss", Locale.US).format(Date())
        
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_$timeStr.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/SecurityCam/$dateStr")
            }
        }
        
        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    captureCount.value++
                    lastCaptureTime = System.currentTimeMillis()
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("SecurityCam", "Photo capture failed", exception)
                }
            }
        )
    }

    private fun cleanUpOldPhotos() {
        CoroutineScope(Dispatchers.IO).launch {
            val retentionDays = settingsRepo.retentionDays.first()
            val cutoff = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.Media.DATE_ADDED} < ?"
                    val selectionArgs = arrayOf("%SecurityCam%", (cutoff / 1000).toString())
                    contentResolver.delete(uri, selection, selectionArgs)
                } catch (e: Exception) {
                    Log.e("SecurityCam", "MediaStore cleanup failed", e)
                }
            } else {
                val baseDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "SecurityCam")
                if (baseDir.exists()) {
                    baseDir.listFiles()?.forEach { dateDir ->
                        if (dateDir.isDirectory) {
                            try {
                                val dateStr = dateDir.name
                                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr)
                                if (date != null && date.time < cutoff) {
                                    dateDir.deleteRecursively()
                                }
                            } catch (e: Exception) {
                                Log.e("SecurityCam", "Cleanup parse error", e)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        isRunning.value = false
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        captureJob?.cancel()
        serviceJob?.cancel()
        cameraProvider?.unbindAll()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        super.onDestroy()
    }
}
