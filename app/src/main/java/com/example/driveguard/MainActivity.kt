package com.example.driveguard

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.os.Bundle
import android.os.SystemClock
import android.util.Size
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.driveguard.data.AppDatabase
import com.example.driveguard.data.TrafficLightRepository
import com.example.driveguard.databinding.ActivityMainBinding
import com.example.driveguard.service.LocationTrackingService
import com.example.driveguard.ui.TrafficLightDashboard
import com.example.driveguard.ui.TrafficLightViewModel
import java.util.concurrent.Executors
import kotlin.math.atan

private const val DEFAULT_RTSP_URL = "rtsp://192.168.0.1:554/livestream/12"
private const val PREFS_NAME = "driveguard_prefs"
private const val KEY_RTSP_URL = "saved_rtsp_url"

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private lateinit var alerts: AlertManager
    private lateinit var speed: SpeedProvider
    private var detector: HazardDetector? = null
    private val analyzer = HazardAnalyzer()
    private val exec = Executors.newSingleThreadExecutor()
    private var lastTs = 0L
    private var fps = 0f

    var isVisionEnabled = true
    private var isRtspMode = false
    private var rtspConnected = false
    private var rtspManager: RtspCameraManager? = null

    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            speed.start()
            try {
                val serviceIntent = Intent(this, LocationTrackingService::class.java)
                ContextCompat.startForegroundService(this, serviceIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (!isRtspMode && isVisionEnabled && r[Manifest.permission.CAMERA] == true) {
            startCamera()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.preview.scaleType = PreviewView.ScaleType.FIT_CENTER
        alerts = AlertManager(this)
        speed = SpeedProvider(this)
        setupThresholdSliders()
        setupRtspControls()
        setupVisionToggle()
        setupDrawerControls()
        setupTrafficDashboard()
        try {
            detector = HazardDetector(this)
        } catch (e: Throwable) {
            b.bannerCard.visibility = View.VISIBLE
            b.banner.text = "Model missing: add ${HazardDetector.MODEL} to app/src/main/assets"
        }
        perms.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION))
    }

    private fun setupTrafficDashboard() {
        val db = AppDatabase.getInstance(applicationContext)
        val repository = TrafficLightRepository(db.trafficLightDao())
        val viewModel = TrafficLightViewModel(repository)
        b.trafficLightComposeView.setContent {
            TrafficLightDashboard(viewModel = viewModel)
        }
    }

    private fun setupDrawerControls() {
        b.btnToggleSettings.setOnClickListener {
            val isVisible = b.thresholdsScrollView.visibility == View.VISIBLE
            b.thresholdsScrollView.visibility = if (isVisible) View.GONE else View.VISIBLE
        }
    }

    private fun setupVisionToggle() {
        b.toggleVisionSwitch.isChecked = isVisionEnabled
        b.toggleVisionSwitch.setOnCheckedChangeListener { _, isChecked ->
            isVisionEnabled = isChecked
            if (!isChecked) {
                runOnUiThread {
                    b.overlay.clear()
                    b.overlay.visibility = View.GONE
                    b.preview.visibility = View.GONE
                    b.rtspPreview.visibility = View.GONE
                    b.bannerCard.visibility = View.GONE
                    stopCameraX()
                    rtspManager?.stop()
                    b.status.text = "Vision functions disabled"
                }
            } else {
                runOnUiThread {
                    b.overlay.visibility = View.VISIBLE
                    if (isRtspMode) {
                        b.preview.visibility = View.GONE
                        b.rtspPreview.visibility = View.VISIBLE
                        if (rtspConnected) {
                            val input = b.rtspUrlInput.text.toString()
                            val parsedUrl = RtspUtils.parseRtspUrl(input)
                            if (parsedUrl != null) {
                                rtspManager?.start(parsedUrl)
                            }
                        }
                    } else {
                        b.rtspPreview.visibility = View.GONE
                        b.preview.visibility = View.VISIBLE
                        startCamera()
                    }
                    b.status.text = ""
                }
            }
        }
    }

    private fun setupRtspControls() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val initialUrl = prefs.getString(KEY_RTSP_URL, DEFAULT_RTSP_URL) ?: DEFAULT_RTSP_URL
        b.rtspUrlInput.setText(initialUrl)

        rtspManager = RtspCameraManager(
            textureView = b.rtspPreview,
            executorService = exec,
            onFrameAvailable = ::processRtspFrame,
            onError = { errMsg ->
                runOnUiThread {
                    rtspConnected = false
                    b.rtspConnectBtn.text = "Connect"
                    Toast.makeText(this, errMsg, Toast.LENGTH_LONG).show()
                }
            }
        )

        b.toggleSourceBtn.setOnClickListener {
            isRtspMode = !isRtspMode
            if (isRtspMode) {
                b.toggleSourceBtn.text = "Source: RTSP"
                b.preview.visibility = View.GONE
                if (isVisionEnabled) {
                    b.rtspPreview.visibility = View.VISIBLE
                }
                b.rtspInputContainer.visibility = View.VISIBLE
                stopCameraX()
            } else {
                b.toggleSourceBtn.text = "Source: Camera"
                b.rtspPreview.visibility = View.GONE
                b.rtspInputContainer.visibility = View.GONE
                rtspManager?.stop()
                rtspConnected = false
                b.rtspConnectBtn.text = "Connect"
                if (isVisionEnabled) {
                    b.preview.visibility = View.VISIBLE
                    startCamera()
                }
            }
        }

        b.rtspConnectBtn.setOnClickListener {
            if (rtspConnected) {
                rtspManager?.stop()
                rtspConnected = false
                b.rtspConnectBtn.text = "Connect"
            } else {
                val input = b.rtspUrlInput.text.toString()
                val parsedUrl = RtspUtils.parseRtspUrl(input)
                if (parsedUrl == null) {
                    Toast.makeText(this, "Invalid RTSP URL/port. Format: host:port/path", Toast.LENGTH_SHORT).show()
                } else {
                    b.rtspUrlInput.setText(parsedUrl)
                    prefs.edit().putString(KEY_RTSP_URL, parsedUrl).apply()
                    rtspConnected = true
                    b.rtspConnectBtn.text = "Disconnect"
                    rtspManager?.start(parsedUrl)
                }
            }
        }
    }

    private fun setupThresholdSliders() {
        b.safeHeadwaySeek.progress = (analyzer.safeHeadwayS * 10f).toInt()
        b.safeHeadwayLabel.text = "Safe Headway: %.1fs".format(analyzer.safeHeadwayS)
        b.safeHeadwaySeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val v = progress / 10f
                analyzer.safeHeadwayS = v
                b.safeHeadwayLabel.text = "Safe Headway: %.1fs".format(v)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        b.critHeadwaySeek.progress = (analyzer.critHeadwayS * 10f).toInt()
        b.critHeadwayLabel.text = "Critical Headway: %.1fs".format(analyzer.critHeadwayS)
        b.critHeadwaySeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val v = progress / 10f
                analyzer.critHeadwayS = v
                b.critHeadwayLabel.text = "Critical Headway: %.1fs".format(v)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        b.critTtcSeek.progress = (analyzer.critTtcS * 10f).toInt()
        b.critTtcLabel.text = "Critical TTC: %.1fs".format(analyzer.critTtcS)
        b.critTtcSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val v = progress / 10f
                analyzer.critTtcS = v
                b.critTtcLabel.text = "Critical TTC: %.1fs".format(v)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        b.critCooldownSeek.progress = (alerts.critCooldownMs / 1000L).toInt()
        b.critCooldownLabel.text = "Crit Cooldown: ${alerts.critCooldownMs / 1000L}s"
        b.critCooldownSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                alerts.critCooldownMs = progress * 1000L
                b.critCooldownLabel.text = "Crit Cooldown: ${progress}s"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        b.warnCooldownSeek.progress = (alerts.warnCooldownMs / 1000L).toInt()
        b.warnCooldownLabel.text = "Warn Cooldown: ${alerts.warnCooldownMs / 1000L}s"
        b.warnCooldownSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                alerts.warnCooldownMs = progress * 1000L
                b.warnCooldownLabel.text = "Warn Cooldown: ${progress}s"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        b.infoCooldownSeek.progress = (alerts.infoCooldownMs / 1000L).toInt()
        b.infoCooldownLabel.text = "Info Cooldown: ${alerts.infoCooldownMs / 1000L}s"
        b.infoCooldownSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                alerts.infoCooldownMs = progress * 1000L
                b.infoCooldownLabel.text = "Info Cooldown: ${progress}s"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun startCamera() {
        if (!isVisionEnabled) return
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val ratio = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
            val preview = Preview.Builder()
                .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratio).build())
                .build().also { it.setSurfaceProvider(b.preview.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder().setAspectRatioStrategy(ratio)
                        .setResolutionStrategy(
                            ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                        ).build()
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build().also { it.setAnalyzer(exec, ::analyze) }
            provider.unbindAll()
            val cam = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            analyzer.hFovDeg = horizontalFov(cam) ?: 66f
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCameraX() {
        try {
            val future = ProcessCameraProvider.getInstance(this)
            future.addListener({
                future.get().unbindAll()
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    private fun horizontalFov(cam: Camera): Float? = try {
        val info = Camera2CameraInfo.from(cam.cameraInfo)
        val focal = info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
        val sensor = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        if (focal != null && sensor != null)
            Math.toDegrees(2.0 * atan((sensor.width / (2f * focal)).toDouble())).toFloat()
        else null
    } catch (e: Exception) { null }

    private fun analyze(img: ImageProxy) {
        try {
            if (isRtspMode) return
            val raw = img.toBitmap()
            val rot = img.imageInfo.rotationDegrees
            val bmp = if (rot == 0) raw else {
                val rotated = Bitmap.createBitmap(
                    raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rot.toFloat()) }, true
                )
                if (rotated != raw) raw.recycle()
                rotated
            }
            try {
                processBitmap(bmp)
            } finally {
                if (bmp != raw) bmp.recycle()
                raw.recycle()
            }
        } finally {
            img.close()
        }
    }

    private fun processRtspFrame(bmp: Bitmap) {
        if (!isRtspMode) return
        processBitmap(bmp)
    }

    private fun processBitmap(bmp: Bitmap) {
        if (!isVisionEnabled) {
            runOnUiThread {
                b.overlay.clear()
                b.bannerCard.visibility = View.GONE
            }
            return
        }
        val det = detector ?: return
        val now = SystemClock.elapsedRealtime()
        val spd = speed.current
        val dets = det.detect(bmp)
        val hazards = analyzer.analyze(bmp, dets, spd, now)
        alerts.handle(hazards)
        if (lastTs != 0L) fps = 0.9f * fps + 0.1f * (1000f / (now - lastTs).coerceAtLeast(1L))
        lastTs = now
        val res = FrameResult(dets, hazards, bmp.width, bmp.height)
        runOnUiThread { render(res, spd) }
    }

    private fun render(res: FrameResult, spd: Float?) {
        b.overlay.show(res)
        val top = res.hazards.firstOrNull()
        if (top == null) {
            b.bannerCard.visibility = View.GONE
        } else {
            b.bannerCard.visibility = View.VISIBLE
            b.banner.text = top.message
            val bgDrawable = ContextCompat.getDrawable(this, R.drawable.bg_status_pill)?.mutate()
            val tintColor = when (top.severity) {
                Severity.CRITICAL -> 0xEEEF4444.toInt()
                Severity.WARNING -> 0xEEF59E0B.toInt()
                Severity.INFO -> 0xEE10B981.toInt()
            }
            bgDrawable?.setTint(tintColor)
            b.bannerCard.background = bgDrawable
        }
        val kmh = spd?.let { "%.0f km/h".format(it * 3.6f) } ?: "-- km/h"
        b.status.text = "$kmh  ·  ${"%.0f".format(fps)} fps"
    }

    override fun onDestroy() {
        super.onDestroy()
        rtspManager?.stop()
        speed.stop()
        alerts.shutdown()
        exec.execute { detector?.close() } // runs after any in-flight frame
        exec.shutdown()
    }
}
