package com.example.driveguard

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.view.Surface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface VideoFrameEncoder {
    fun start(outputFile: File, width: Int, height: Int)
    fun encodeFrame(bitmap: Bitmap, timestampNs: Long)
    fun stop()
}

class MediaCodecVideoEncoder : VideoFrameEncoder {
    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var inputSurface: Surface? = null
    private var trackIndex = -1
    private var muxerStarted = false
    private val bufferInfo = MediaCodec.BufferInfo()
    private var isStarted = false

    override fun start(outputFile: File, width: Int, height: Int) {
        if (isStarted) return
        try {
            val encWidth = if (width % 2 == 0) width else width - 1
            val encHeight = if (height % 2 == 0) height else height - 1

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, encWidth, encHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            val mCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            mCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = mCodec.createInputSurface()
            mCodec.start()
            codec = mCodec

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            trackIndex = -1
            muxerStarted = false
            isStarted = true
        } catch (e: Exception) {
            e.printStackTrace()
            stop()
        }
    }

    override fun encodeFrame(bitmap: Bitmap, timestampNs: Long) {
        if (!isStarted) return
        val surface = inputSurface ?: return
        try {
            val canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                surface.lockHardwareCanvas()
            } else {
                surface.lockCanvas(null)
            }
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            surface.unlockCanvasAndPost(canvas)

            drainEncoder(endOfStream = false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun drainEncoder(endOfStream: Boolean) {
        val mCodec = codec ?: return
        val mMuxer = muxer ?: return

        if (endOfStream) {
            try {
                mCodec.signalEndOfInputStream()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val timeoutUs = 10000L
        while (true) {
            val encoderStatus = try {
                mCodec.dequeueOutputBuffer(bufferInfo, timeoutUs)
            } catch (e: Exception) {
                break
            }

            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!muxerStarted) {
                    val newFormat = mCodec.outputFormat
                    trackIndex = mMuxer.addTrack(newFormat)
                    mMuxer.start()
                    muxerStarted = true
                }
            } else if (encoderStatus >= 0) {
                val encodedData = mCodec.getOutputBuffer(encoderStatus)
                if (encodedData != null && bufferInfo.size > 0 && muxerStarted) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    mMuxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                }
                mCodec.releaseOutputBuffer(encoderStatus, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }
        }
    }

    override fun stop() {
        if (!isStarted) return
        isStarted = false
        try {
            drainEncoder(endOfStream = true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            codec?.stop()
            codec?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            codec = null
        }
        try {
            if (muxerStarted) {
                muxer?.stop()
            }
            muxer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            muxer = null
            muxerStarted = false
        }
        try {
            inputSurface?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            inputSurface = null
        }
    }
}

class ObjectVideoRecorder(
    @Volatile var isEnabled: Boolean = false,
    @Volatile var saveDirectoryPath: String = "",
    var disappearanceTimeoutMs: Long = 1500L,
    val targetObjects: Set<String> = DEFAULT_TARGET_OBJECTS,
    private val encoderFactory: () -> VideoFrameEncoder = { MediaCodecVideoEncoder() },
    private val onRecordingStateChanged: ((isRecording: Boolean, filePath: String?) -> Unit)? = null
) {
    @Volatile
    var isRecording: Boolean = false
        private set

    @Volatile
    var currentOutputFile: File? = null
        private set

    private var currentEncoder: VideoFrameEncoder? = null
    private var lastTargetSeenMs: Long = 0L

    @Synchronized
    fun processFrame(bitmap: Bitmap, detections: List<Det>, timestampMs: Long) {
        if (!isEnabled) {
            if (isRecording) {
                stopRecording()
            }
            return
        }

        val hasTarget = detections.any { it.label in targetObjects }

        if (hasTarget) {
            lastTargetSeenMs = timestampMs
            if (!isRecording) {
                startRecording(bitmap.width, bitmap.height, timestampMs)
            }
            currentEncoder?.encodeFrame(bitmap, timestampMs * 1_000_000L)
        } else {
            if (isRecording) {
                currentEncoder?.encodeFrame(bitmap, timestampMs * 1_000_000L)
                if (timestampMs - lastTargetSeenMs >= disappearanceTimeoutMs) {
                    stopRecording()
                }
            }
        }
    }

    @Synchronized
    private fun startRecording(width: Int, height: Int, timestampMs: Long) {
        val dirPath = saveDirectoryPath.ifBlank { return }
        val dir = File(dirPath)
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val timeStampStr = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val file = File(dir, "DriveGuard_$timeStampStr.mp4")

        try {
            val encoder = encoderFactory()
            encoder.start(file, width, height)
            currentEncoder = encoder
            currentOutputFile = file
            isRecording = true
            onRecordingStateChanged?.invoke(true, file.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
            isRecording = false
            currentEncoder = null
            currentOutputFile = null
        }
    }

    @Synchronized
    fun stopRecording() {
        if (!isRecording && currentEncoder == null) return
        val file = currentOutputFile
        try {
            currentEncoder?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            currentEncoder = null
            isRecording = false
            currentOutputFile = null
            onRecordingStateChanged?.invoke(false, file?.absolutePath)
        }
    }

    companion object {
        val DEFAULT_TARGET_OBJECTS = setOf("person", "car")
    }
}
