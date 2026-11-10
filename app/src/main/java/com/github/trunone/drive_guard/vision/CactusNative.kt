package com.github.trunone.drive_guard.vision

object CactusNative {
    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("cactus_driver")
            isNativeLoaded = true
        } catch (t: Throwable) {
            isNativeLoaded = false
        }
    }

    val isAvailable: Boolean
        get() = isNativeLoaded

    external fun load(modelBytes: ByteArray): Int
    external fun models(): Int
    external fun lastError(): String
    external fun describe(rgbBytes: ByteArray, width: Int, height: Int, optionsJson: String?): String?
}
