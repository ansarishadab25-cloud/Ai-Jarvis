package com.example.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.model.SpeechLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TextToSpeechManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var activeOnDone: (() -> Unit)? = null
    private var activeOnError: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            setupLocale(SpeechLanguage.HINDI)
            setupListener()
        } else {
            isInitialized = false
        }
    }

    private fun setupLocale(language: SpeechLanguage) {
        val targetLocale = when (language) {
            SpeechLanguage.HINDI,
            SpeechLanguage.HINGLISH -> Locale("hi", "IN")
            SpeechLanguage.ENGLISH -> Locale("en", "IN")
        }

        val result = tts?.setLanguage(targetLocale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Fallback to Indian English or default locale
            val fallback = Locale("en", "IN")
            tts?.setLanguage(fallback)
        }
    }

    private fun setupListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                val callback = activeOnDone
                activeOnDone = null
                callback?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                val callback = activeOnError
                activeOnError = null
                callback?.invoke()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking.value = false
                val callback = activeOnError
                activeOnError = null
                callback?.invoke()
            }
        })
    }

    fun speak(
        text: String,
        language: SpeechLanguage = SpeechLanguage.HINDI,
        speechRate: Float = 1.0f,
        onStart: (() -> Unit)? = null,
        onDone: (() -> Unit)? = null,
        onError: (() -> Unit)? = null
    ) {
        if (!isInitialized || tts == null) {
            onError?.invoke()
            return
        }

        stop()
        setupLocale(language)
        tts?.setSpeechRate(speechRate.coerceIn(0.5f, 2.0f))
        tts?.setPitch(1.05f) // Slightly higher tone for futuristic assistant feel

        activeOnDone = onDone
        activeOnError = onError
        onStart?.invoke()

        val utteranceId = "JARVIS_TTS_${System.currentTimeMillis()}"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        try {
            if (tts?.isSpeaking == true) {
                tts?.stop()
            }
        } catch (_: Exception) {
        } finally {
            _isSpeaking.value = false
            activeOnDone = null
            activeOnError = null
        }
    }

    fun shutdown() {
        try {
            stop()
            tts?.shutdown()
        } catch (_: Exception) {
        } finally {
            tts = null
            isInitialized = false
        }
    }
}
