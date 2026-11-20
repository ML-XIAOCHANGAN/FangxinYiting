package com.fangxin.yiting.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator

class Cues(context: Context) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var tone: ToneGenerator? = null
    private var heartbeat: Runnable? = null

    private fun tone(): ToneGenerator? {
        if (tone == null) {
            tone = try {
                ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            } catch (error: Exception) {
                null
            }
        }
        return tone
    }

    private fun vibrate(pattern: LongArray) {
        val vibrator = appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    fun start() {
        tone()?.startTone(ToneGenerator.TONE_PROP_BEEP, 110)
        vibrate(longArrayOf(0, 60))
    }

    fun stopTone() {
        tone()?.startTone(ToneGenerator.TONE_PROP_ACK, 140)
        vibrate(longArrayOf(0, 40, 80, 40))
    }

    fun error() {
        tone()?.startTone(ToneGenerator.TONE_PROP_NACK, 220)
        vibrate(longArrayOf(0, 90, 70, 90, 70, 90))
    }

    fun thinking() {
        tone()?.startTone(ToneGenerator.TONE_PROP_ACK, 90)
    }

    // 想的时候一直轻轻滴答，让老人知道还在等，不是死机。
    fun startHeartbeat() {
        stopHeartbeat()
        val runnable = object : Runnable {
            override fun run() {
                tone()?.startTone(ToneGenerator.TONE_PROP_BEEP, 55)
                handler.postDelayed(this, 1250)
            }
        }
        heartbeat = runnable
        handler.postDelayed(runnable, 1250)
    }

    fun stopHeartbeat() {
        heartbeat?.let { handler.removeCallbacks(it) }
        heartbeat = null
    }

    fun verdictRed() {
        vibrate(longArrayOf(0, 320, 100, 320, 100, 480))
        val generator = tone() ?: return
        generator.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 220)
        handler.postDelayed({ generator.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 220) }, 330)
        handler.postDelayed({ generator.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 420) }, 660)
    }

    fun verdictAmber() {
        vibrate(longArrayOf(0, 160, 90, 160))
        val generator = tone() ?: return
        generator.startTone(ToneGenerator.TONE_PROP_ACK, 160)
        handler.postDelayed({ generator.startTone(ToneGenerator.TONE_PROP_ACK, 160) }, 260)
    }

    fun verdictGreen() {
        vibrate(longArrayOf(0, 40))
        tone()?.startTone(ToneGenerator.TONE_PROP_BEEP2, 160)
    }

    fun release() {
        stopHeartbeat()
        tone?.release()
        tone = null
    }
}
