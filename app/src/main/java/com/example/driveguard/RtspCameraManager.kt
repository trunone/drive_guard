package com.example.driveguard

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.TextureView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
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

    @Volatile
    var lastFrameTimeMs = 0L
        private set

    private var lastVideoWidth = 0
    private var lastVideoHeight = 0

    var watchdogTimeoutMs = 4000L
    private val watchdogCheckIntervalMs = 1000L

    var bufferingTimeoutMs = 4000L

    private val mainHandler = Handler(Looper.getMainLooper())

    private val bufferingTimeoutRunnable = Runnable {
        val urlToRestart = currentUrl
        if (urlToRestart != null) {
            stop()
            start(urlToRestart)
        }
    }

    private val watchdogRunnable = object : Runnable {
        override fun run() {
            if (currentUrl != null && exoPlayer != null) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastFrameTimeMs >= watchdogTimeoutMs) {
                    val urlToRestart = currentUrl
                    if (urlToRestart != null) {
                        stop()
                        start(urlToRestart)
                    }
                    return
                }
                mainHandler.postDelayed(this, watchdogCheckIntervalMs)
            }
        }
    }

    private val frameMetadataListener = VideoFrameMetadataListener { _, _, _, _ ->
        lastFrameTimeMs = SystemClock.elapsedRealtime()
        mainHandler.post {
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
                    isPlaying = true
                    mainHandler.removeCallbacks(bufferingTimeoutRunnable)
                }
                Player.STATE_ENDED, Player.STATE_IDLE -> {
                    isPlaying = false
                    isProcessingFrame.set(false)
                    mainHandler.removeCallbacks(bufferingTimeoutRunnable)
                }
                Player.STATE_BUFFERING -> {
                    mainHandler.removeCallbacks(bufferingTimeoutRunnable)
                    mainHandler.postDelayed(bufferingTimeoutRunnable, bufferingTimeoutMs)
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            isPlaying = false
            isProcessingFrame.set(false)
            mainHandler.removeCallbacks(bufferingTimeoutRunnable)
            onError("RTSP playback error: ${error.localizedMessage ?: error.message}")
        }
    }

    fun start(rtspUrl: String) {
        stop()
        currentUrl = rtspUrl
        lastFrameTimeMs = SystemClock.elapsedRealtime()
        try {
            val mediaSource = RtspMediaSource.Factory()
                .setForceUseRtpTcp(true)
                .createMediaSource(MediaItem.fromUri(rtspUrl))

            val player = ExoPlayer.Builder(textureView.context).build().apply {
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .build()
                setMediaSource(mediaSource)
                addListener(playerListener)
                setVideoTextureView(textureView)
                setVideoFrameMetadataListener(frameMetadataListener)
                prepare()
                playWhenReady = true
            }
            exoPlayer = player
            mainHandler.removeCallbacks(watchdogRunnable)
            mainHandler.postDelayed(watchdogRunnable, watchdogCheckIntervalMs)
        } catch (e: Exception) {
            onError("Failed to start RTSP player: ${e.localizedMessage}")
        }
    }

    fun stop() {
        isPlaying = false
        isProcessingFrame.set(false)
        mainHandler.removeCallbacks(watchdogRunnable)
        mainHandler.removeCallbacks(bufferingTimeoutRunnable)
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
