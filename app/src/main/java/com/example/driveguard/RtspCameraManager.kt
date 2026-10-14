package com.example.driveguard

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

class RtspCameraManager(
    private val textureView: TextureView,
    private val executorService: ExecutorService,
    private val onFrameAvailable: (Bitmap) -> Unit,
    private val onError: (String) -> Unit
) : TextureView.SurfaceTextureListener,
    MediaPlayer.OnPreparedListener,
    MediaPlayer.OnErrorListener,
    MediaPlayer.OnVideoSizeChangedListener {

    private var mediaPlayer: MediaPlayer? = null
    private var surface: Surface? = null
    private var currentUrl: String? = null
    private var isPlaying = false
    private val isProcessingFrame = AtomicBoolean(false)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val frameCaptureRunnable = object : Runnable {
        override fun run() {
            if (isPlaying && textureView.isAvailable) {
                if (isProcessingFrame.compareAndSet(false, true)) {
                    val bitmap = textureView.getBitmap(640, 480) ?: textureView.bitmap
                    if (bitmap != null) {
                        executorService.execute {
                            try {
                                onFrameAvailable(bitmap)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            } finally {
                                bitmap.recycle()
                                isProcessingFrame.set(false)
                            }
                        }
                    } else {
                        isProcessingFrame.set(false)
                    }
                }
            }
            if (isPlaying) {
                mainHandler.postDelayed(this, 100) // ~10 FPS frame sampling
            }
        }
    }

    fun start(rtspUrl: String) {
        stop()
        currentUrl = rtspUrl
        textureView.surfaceTextureListener = this
        if (textureView.isAvailable) {
            val surfaceTexture = textureView.surfaceTexture
            if (surfaceTexture != null) {
                onSurfaceTextureAvailable(surfaceTexture, textureView.width, textureView.height)
            }
        }
    }

    fun stop() {
        isPlaying = false
        mainHandler.removeCallbacks(frameCaptureRunnable)
        isProcessingFrame.set(false)
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaPlayer = null
        }

        surface?.release()
        surface = null
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        val url = currentUrl ?: return
        try {
            surface = Surface(surfaceTexture)
            mediaPlayer = MediaPlayer().apply {
                setSurface(surface)
                val headers = mapOf(
                    "rtsp_transport" to "tcp",
                    "rtsp_flags" to "prefer_tcp"
                )
                setDataSource(textureView.context, Uri.parse(url), headers)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setOnPreparedListener(this@RtspCameraManager)
                setOnErrorListener(this@RtspCameraManager)
                setOnVideoSizeChangedListener(this@RtspCameraManager)
                prepareAsync()
            }
        } catch (e: Exception) {
            onError("Failed to start RTSP player: ${e.localizedMessage}")
        }
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        mediaPlayer?.let { mp ->
            if (mp.videoWidth > 0 && mp.videoHeight > 0) {
                adjustAspectRatio(mp.videoWidth, mp.videoHeight)
            }
        }
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        stop()
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}

    override fun onVideoSizeChanged(mp: MediaPlayer?, width: Int, height: Int) {
        if (width > 0 && height > 0) {
            adjustAspectRatio(width, height)
        }
    }

    private fun adjustAspectRatio(videoWidth: Int, videoHeight: Int) {
        val viewWidth = textureView.width
        val viewHeight = textureView.height
        if (viewWidth <= 0 || viewHeight <= 0) return

        val viewRatio = viewWidth.toDouble() / viewHeight
        val videoRatio = videoWidth.toDouble() / videoHeight

        var scaleX = 1f
        var scaleY = 1f

        if (videoRatio > viewRatio) {
            scaleY = (viewRatio / videoRatio).toFloat()
        } else {
            scaleX = (videoRatio / viewRatio).toFloat()
        }

        val pivotX = viewWidth / 2f
        val pivotY = viewHeight / 2f

        val matrix = Matrix()
        matrix.setScale(scaleX, scaleY, pivotX, pivotY)
        mainHandler.post {
            textureView.setTransform(matrix)
        }
    }

    override fun onPrepared(mp: MediaPlayer) {
        try {
            if (mp.videoWidth > 0 && mp.videoHeight > 0) {
                adjustAspectRatio(mp.videoWidth, mp.videoHeight)
            }
            mp.start()
            isPlaying = true
            mainHandler.post(frameCaptureRunnable)
        } catch (e: Exception) {
            onError("Failed to play RTSP stream: ${e.localizedMessage}")
        }
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        isPlaying = false
        mainHandler.removeCallbacks(frameCaptureRunnable)
        isProcessingFrame.set(false)
        onError("RTSP playback error (what: $what, extra: $extra)")
        return true
    }
}
