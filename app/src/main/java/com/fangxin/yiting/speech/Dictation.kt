package com.fangxin.yiting.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class Dictation(
    context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (Int) -> Unit,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var lastPartial = ""
    private var stopped = false

    val available: Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    fun start() {
        if (!available) {
            onError(SpeechRecognizer.ERROR_CLIENT)
            return
        }
        stop()
        stopped = false
        lastPartial = ""
        mainHandler.post {
            val engine = SpeechRecognizer.createSpeechRecognizer(appContext)
            recognizer = engine
            engine.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    if (!stopped) engine.stopListening()
                }

                override fun onError(error: Int) {
                    if (stopped) return
                    stopped = true
                    val partial = lastPartial
                    release()
                    if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    ) {
                        if (partial.isNotBlank()) onFinal(partial) else onFinal("")
                        return
                    }
                    onError(error)
                }

                override fun onResults(results: Bundle?) {
                    if (stopped) return
                    stopped = true
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    val partial = lastPartial
                    release()
                    onFinal(text.ifBlank { partial })
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (text.isNotBlank()) {
                        lastPartial = text
                        onPartial(text)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            }
            engine.startListening(intent)
        }
    }

    fun stop() {
        stopped = true
        release()
    }

    private fun release() {
        val engine = recognizer ?: return
        recognizer = null
        mainHandler.post {
            try {
                engine.stopListening()
            } catch (error: Exception) {
                // 识别器可能已经结束
            }
            try {
                engine.destroy()
            } catch (error: Exception) {
                // 同上
            }
        }
    }
}
