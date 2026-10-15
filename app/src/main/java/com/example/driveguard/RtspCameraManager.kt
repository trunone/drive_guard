package com.example.driveguard

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.view.TextureView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

class RtspCameraManager(
    private val textureView: TextureView,
    private val executorService: ExecutorService,
    private val onFrameAvailable: (Bitmap) -> Unit,
    private val onError: (String) -> Unit
) {
    private var exoPlayer: ExoPlayer? = null
    private var currentUrl: String? = null
    private var isPlaying = false
    private val isProcessingFrame = AtomicBoolean(false)

    private var lastVideoWidth = 0
    private var lastVideoHeight = 0

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

    private val playerListener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                lastVideoWidth = videoSize.width
                lastVideoHeight = videoSize.height
                textureView.post {
                    adjustAspectRatio(videoSize.width, videoSize.height)
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    if (!isPlaying) {
                        isPlaying = true
                        mainHandler.removeCallbacks(frameCaptureRunnable)
                        mainHandler.post(frameCaptureRunnable)
                    }
                }
                Player.STATE_ENDED, Player.STATE_IDLE -> {
                    isPlaying = false
                    mainHandler.removeCallbacks(frameCaptureRunnable)
                    isProcessingFrame.set(false)
                }
                Player.STATE_BUFFERING -> {
                    // Stream buffering
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            isPlaying = false
            mainHandler.removeCallbacks(frameCaptureRunnable)
            isProcessingFrame.set(false)
            onError("RTSP playback error: ${error.localizedMessage ?: error.message}")
        }
    }

    fun start(rtspUrl: String) {
        stop()
        currentUrl = rtspUrl
        try {
            val mediaSource = RtspMediaSource.Factory()
                .setForceUseRtpTcp(true)
                .createMediaSource(MediaItem.fromUri(rtspUrl))

            val player = ExoPlayer.Builder(textureView.context).build().apply {
                setMediaSource(mediaSource)
                addListener(playerListener)
                setVideoTextureView(textureView)
                prepare()
                playWhenReady = true
            }
            exoPlayer = player
        } catch (e: Exception) {
            onError("Failed to start RTSP player: ${e.localizedMessage}")
        }
    }

    fun stop() {
        isPlaying = false
        mainHandler.removeCallbacks(frameCaptureRunnable)
        isProcessingFrame.set(false)
        try {
            exoPlayer?.apply {
                stop()
                clearVideoTextureView(textureView)
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            exoPlayer = null
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
}
