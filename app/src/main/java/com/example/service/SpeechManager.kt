package com.example.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.model.SpeechLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class SpeechManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    fun isSpeechRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening(
        language: SpeechLanguage,
        onResult: (String) -> Unit,
        onError: (String, String, Boolean) -> Unit
    ) {
        if (!isSpeechRecognitionAvailable()) {
            onError(
                "इस डिवाइस पर स्पीच रिकग्निशन उपलब्ध नहीं है।",
                "Speech recognition is not available on this device.",
                false
            )
            return
        }

        stopListening()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                        _partialText.value = ""
                    }

                    override fun onBeginningOfSpeech() {
                        _isListening.value = true
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        // Normalize RMS dB typically in range -2 to 10
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                        _rmsLevel.value = normalized
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _isListening.value = false
                        _rmsLevel.value = 0f
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        _rmsLevel.value = 0f
                        val (hindiMsg, englishMsg, isOffline) = mapErrorCodeToMessage(error)
                        onError(hindiMsg, englishMsg, isOffline)
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        _rmsLevel.value = 0f
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim()
                        if (!text.isNullOrEmpty()) {
                            _partialText.value = text
                            onResult(text)
                        } else {
                            onError(
                                "कोई आवाज़ समझ नहीं आई। कृपया दोबारा बोलें।",
                                "No speech detected. Please speak again.",
                                false
                            )
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull()
                        if (!partial.isNullOrEmpty()) {
                            _partialText.value = partial
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

                val targetTag = when (language) {
                    SpeechLanguage.HINDI -> "hi-IN"
                    SpeechLanguage.HINGLISH -> "hi-IN"
                    SpeechLanguage.ENGLISH -> "en-IN"
                }

                putExtra(RecognizerIntent.EXTRA_LANGUAGE, targetTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, targetTag)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            _isListening.value = false
            onError(
                "माइक शुरू करने में समस्या आई: ${e.localizedMessage}",
                "Failed to initialize speech: ${e.localizedMessage}",
                false
            )
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        } finally {
            speechRecognizer = null
            _isListening.value = false
            _rmsLevel.value = 0f
        }
    }

    private fun mapErrorCodeToMessage(errorCode: Int): Triple<String, String, Boolean> {
        return when (errorCode) {
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> Triple(
                "नेटवर्क समस्या: कृपया इंटरनेट जांचें या ऑफ़लाइन वॉयस पैक डाउनलोड करें।",
                "Network error: Please check your connection or download offline speech data.",
                true
            )
            SpeechRecognizer.ERROR_NO_MATCH -> Triple(
                "कोई आवाज़ पहचानी नहीं गई। कृपया थोड़ा स्पष्ट बोलें।",
                "No speech match recognized. Please speak clearly.",
                false
            )
            SpeechRecognizer.ERROR_AUDIO -> Triple(
                "ऑडियो रिकॉर्डिंग में त्रुटि हुई।",
                "Audio recording error.",
                false
            )
            SpeechRecognizer.ERROR_CLIENT -> Triple(
                "स्पीच सर्विस व्यस्त है या उपलब्ध नहीं है।",
                "Speech client error occurred.",
                false
            )
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Triple(
                "माइक्रोफ़ोन अनुमति की आवश्यकता है। कृपया अनुमति दें।",
                "Microphone permission required.",
                false
            )
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Triple(
                "समय समाप्त: कोई आवाज़ नहीं सुनाई दी।",
                "Listening timed out. No speech was detected.",
                false
            )
            else -> Triple(
                "माइक में त्रुटि (कोड: $errorCode)। कृपया पुनः प्रयास करें।",
                "Speech error (Code: $errorCode). Please retry.",
                false
            )
        }
    }
}
