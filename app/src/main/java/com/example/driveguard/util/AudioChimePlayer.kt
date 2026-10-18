package com.example.driveguard.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator

class AudioChimePlayer(context: Context? = null) {
    private var toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
    } catch (e: Exception) {
        null
    }

    fun playChime() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        try {
            toneGenerator?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            toneGenerator = null
        }
    }
}
