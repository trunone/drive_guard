package com.example.driveguard

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.TextureView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

class RtspCameraManager(
    private val textureView: TextureView,
    private val executorService: ExecutorService,
    private val onFrameAvailable: (Bitmap) -> Unit,
    private val onError: (String) -> Unit
) : TextureView.SurfaceTextureListener {

    private var libVlc: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
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
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.vlcVout.detachViews()
                mp.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaPlayer = null
        }

        try {
            libVlc?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            libVlc = null
        }
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        val url = currentUrl ?: return
        try {
            val options = arrayListOf(
                "--rtsp-tcp",
                "--network-caching=150",
                "--clock-jitter=0",
                "--clock-synchro=0",
                "--drop-late-frames",
                "--skip-frames"
            )
            val vlc = LibVLC(textureView.context, options)
            libVlc = vlc

            val mp = MediaPlayer(vlc)
            mediaPlayer = mp

            val vlcVout = mp.vlcVout
            vlcVout.setVideoSurface(surfaceTexture)
            vlcVout.setWindowSize(width, height)
            vlcVout.attachViews()

            mp.setEventListener { event ->
                when (event.type) {
                    MediaPlayer.Event.Playing -> {
                        isPlaying = true
                        mainHandler.post {
                            if (isPlaying) {
                                mainHandler.post(frameCaptureRunnable)
                            }
                        }
                    }
                    MediaPlayer.Event.EncounteredError -> {
                        isPlaying = false
                        mainHandler.removeCallbacks(frameCaptureRunnable)
                        isProcessingFrame.set(false)
                        mainHandler.post {
                            onError("LibVLC playback error encountered")
                        }
                    }
                    MediaPlayer.Event.Stopped -> {
                        isPlaying = false
                        mainHandler.removeCallbacks(frameCaptureRunnable)
                        isProcessingFrame.set(false)
                    }
                }
            }

            val media = Media(vlc, Uri.parse(url))
            media.setHWDecoderEnabled(true, false)
            media.addOption(":network-caching=150")
            media.addOption(":rtsp-tcp")

            mp.media = media
            media.release()

            mp.play()
        } catch (e: Exception) {
            onError("Failed to start RTSP player: ${e.localizedMessage}")
        }
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        mediaPlayer?.vlcVout?.setWindowSize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        stop()
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
}
