package com.example.driveguard

import android.content.Context
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Speaks hazards, with per-hazard cooldowns so the driver isn't nagged. */
class AlertManager(ctx: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(ctx, this)
    @Volatile private var ready = false
    @Volatile var critCooldownMs = 3_000L
    @Volatile var warnCooldownMs = 6_000L
    @Volatile var infoCooldownMs = 20_000L
    private val last = HashMap<String, Long>()

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) tts.language = Locale.ENGLISH
    }

    fun handle(hazards: List<Hazard>) {
        val now = SystemClock.elapsedRealtime()
        for (h in hazards) {
            val cooldown = when (h.severity) {
                Severity.CRITICAL -> critCooldownMs
                Severity.WARNING -> warnCooldownMs
                Severity.INFO -> infoCooldownMs
            }
            if (now - (last[h.key] ?: 0L) < cooldown) continue
            last[h.key] = now
            if (ready) {
                val mode = if (h.severity == Severity.CRITICAL) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                tts.speak(h.message, mode, null, h.key)
            }
        }
    }

    fun shutdown() { tts.stop(); tts.shutdown() }
}
