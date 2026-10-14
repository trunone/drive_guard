package com.example.driveguard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

object TrapCameraUrlParser {
    fun parseUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("URL cannot be empty")

        val urlString = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://") && !trimmed.startsWith("rtsp://")) {
            "http://$trimmed"
        } else {
            trimmed
        }

        val url = try {
            URL(urlString)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid URL format: ${e.message}")
        }

        if (url.host.isNullOrEmpty()) {
            throw IllegalArgumentException("Invalid host in URL")
        }

        return urlString
    }
}

class TrapCameraFetcher(
    private val rawUrl: String,
    private val onFrameReceived: (Bitmap) -> Unit,
    private val onError: (String) -> Unit
) {
    private val isRunning = AtomicBoolean(false)
    private var workerThread: Thread? = null

    val targetUrl: String = try {
        TrapCameraUrlParser.parseUrl(rawUrl)
    } catch (e: Exception) {
        rawUrl
    }

    fun start() {
        if (isRunning.getAndSet(true)) return
        workerThread = Thread {
            fetchLoop()
        }.apply { start() }
    }

    fun stop() {
        isRunning.set(false)
        workerThread?.interrupt()
        workerThread = null
    }

    private fun fetchLoop() {
        while (isRunning.get()) {
            var conn: HttpURLConnection? = null
            var inputStream: InputStream? = null
            try {
                val url = URL(targetUrl)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    doInput = true
                    connect()
                }

                val contentType = conn.contentType?.lowercase() ?: ""
                inputStream = conn.inputStream

                if (contentType.contains("multipart") || contentType.contains("mjpeg") || contentType.contains("x-mixed-replace")) {
                    readMjpegStream(inputStream)
                } else {
                    val bmp = BitmapFactory.decodeStream(inputStream)
                    if (bmp != null && isRunning.get()) {
                        onFrameReceived(bmp)
                    } else if (bmp == null) {
                        onError("Failed to decode frame from camera stream")
                    }
                    Thread.sleep(50)
                }
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                if (isRunning.get()) {
                    onError("Trap camera error: ${e.message ?: "Connection failed"}")
                    try {
                        Thread.sleep(2000)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            } finally {
                try {
                    inputStream?.close()
                } catch (_: Exception) {
                }
                try {
                    conn?.disconnect()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun readMjpegStream(stream: InputStream) {
        val buffer = ByteArray(4096)
        val frameBuffer = ByteArrayOutputStream()
        var inFrame = false
        var prevByte = -1

        while (isRunning.get()) {
            val count = stream.read(buffer)
            if (count == -1) break

            for (i in 0 until count) {
                val b = buffer[i].toInt() and 0xFF

                if (!inFrame && prevByte == 0xFF && b == 0xD8) {
                    inFrame = true
                    frameBuffer.reset()
                    frameBuffer.write(0xFF)
                    frameBuffer.write(0xD8)
                } else if (inFrame) {
                    frameBuffer.write(b)
                    if (prevByte == 0xFF && b == 0xD9) {
                        inFrame = false
                        val bytes = frameBuffer.toByteArray()
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null && isRunning.get()) {
                            onFrameReceived(bmp)
                        }
                        frameBuffer.reset()
                    }
                }
                prevByte = b
            }
        }
    }
}
