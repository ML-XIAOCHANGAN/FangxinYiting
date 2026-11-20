package com.fangxin.yiting.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

class Tts(context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var ready = false
    private var waiting = false
    private val queue = ArrayDeque<String>()
    private var index = 0
    private var onSentence: ((Int) -> Unit)? = null
    private var pendingDone: (() -> Unit)? = null

    var speaking by mutableStateOf(false)
        private set

    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
                ready = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
                tts.setSpeechRate(0.92f)
                mainHandler.post { if (ready && waiting) next() }
            }
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                mainHandler.post {
                    speaking = true
                    utteranceId?.toIntOrNull()?.let { index -> onSentence?.invoke(index) }
                }
            }

            override fun onDone(utteranceId: String?) {
                mainHandler.post { next() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainHandler.post { finish() }
            }
        })
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        val sentences = text.split(Regex("(?<=[。！？])"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        speakSentences(if (sentences.isEmpty()) listOf(text) else sentences, null, onDone)
    }

    fun speakSentences(
        sentences: List<String>,
        onSentence: ((Int) -> Unit)? = null,
        onDone: (() -> Unit)? = null,
    ) {
        tts.stop()
        queue.clear()
        queue.addAll(sentences)
        index = 0
        this.onSentence = onSentence
        pendingDone = onDone
        waiting = true
        if (ready) next()
    }

    private fun next() {
        if (queue.isEmpty()) {
            finish()
            return
        }
        if (!ready) {
            waiting = true
            return
        }
        waiting = false
        val current = index++
        speaking = true
        onSentence?.invoke(current)
        val result = tts.speak(queue.removeFirst(), TextToSpeech.QUEUE_FLUSH, null, current.toString())
        if (result != TextToSpeech.SUCCESS) finish()
    }

    private fun finish() {
        queue.clear()
        speaking = false
        waiting = false
        val done = pendingDone
        pendingDone = null
        onSentence = null
        done?.invoke()
    }

    fun stop() {
        queue.clear()
        speaking = false
        waiting = false
        pendingDone = null
        onSentence = null
        tts.stop()
    }

    fun shutdown() {
        ready = false
        stop()
        tts.shutdown()
    }
}
