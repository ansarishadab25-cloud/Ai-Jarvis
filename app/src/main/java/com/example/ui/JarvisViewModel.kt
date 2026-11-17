package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CommandDao
import com.example.data.CommandEntity
import com.example.data.JarvisDatabase
import com.example.data.PreferencesManager
import com.example.model.AssistantState
import com.example.model.ChatMessage
import com.example.model.ConfirmationAction
import com.example.model.IntentType
import com.example.model.SpeechLanguage
import com.example.service.CommandResult
import com.example.service.CommandRouter
import com.example.service.SpeechManager
import com.example.service.TextToSpeechManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val speechManager = SpeechManager(application)
    private val ttsManager = TextToSpeechManager(application)
    private val commandRouter = CommandRouter(application)
    private val prefs = PreferencesManager(application)
    private val commandDao: CommandDao = JarvisDatabase.getDatabase(application).commandDao()

    private val _assistantState = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                text = "नमस्ते सर! मैं शादाब एआई जार्विस हूँ। माइक बटन दबाएं या मुझे बोलें: '7 बजे अलार्म लगाओ', '5 मिनट टाइमर', या 'सेटिंग्स खोलो'।",
                isUser = false,
                intentType = IntentType.GENERAL
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    val rmsLevel: StateFlow<Float> = speechManager.rmsLevel
    val partialText: StateFlow<String> = speechManager.partialText

    private val _currentLanguage = MutableStateFlow(
        SpeechLanguage.fromCode(prefs.preferredLanguageCode)
    )
    val currentLanguage: StateFlow<SpeechLanguage> = _currentLanguage.asStateFlow()

    private val _isHandsFreeMode = MutableStateFlow(prefs.isHandsFreeEnabled)
    val isHandsFreeMode: StateFlow<Boolean> = _isHandsFreeMode.asStateFlow()

    private val _showHandsFreeInfo = MutableStateFlow(false)
    val showHandsFreeInfo: StateFlow<Boolean> = _showHandsFreeInfo.asStateFlow()

    private val _showPrivacySheet = MutableStateFlow(false)
    val showPrivacySheet: StateFlow<Boolean> = _showPrivacySheet.asStateFlow()

    private val _isHistoryEnabled = MutableStateFlow(prefs.isHistoryEnabled)
    val isHistoryEnabled: StateFlow<Boolean> = _isHistoryEnabled.asStateFlow()

    val commandHistory: StateFlow<List<CommandEntity>> = commandDao.getAllCommands()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun toggleListening() {
        if (_assistantState.value is AssistantState.Listening || _assistantState.value is AssistantState.Speaking) {
            stopAll()
        } else {
            startListening()
        }
    }

    fun startListening() {
        ttsManager.stop()
        _assistantState.value = AssistantState.Listening(rmsDb = 0f, partialText = "")

        speechManager.startListening(
            language = _currentLanguage.value,
            onResult = { recognizedText ->
                processUserQuery(recognizedText)
            },
            onError = { hindiMsg, engMsg, isOffline ->
                _assistantState.value = AssistantState.Error(
                    messageHindi = hindiMsg,
                    messageEnglish = engMsg,
                    isOffline = isOffline
                )
            }
        )
    }

    fun stopAll() {
        speechManager.stopListening()
        ttsManager.stop()
        _assistantState.value = AssistantState.Idle
    }

    fun processUserQuery(query: String) {
        if (query.isBlank()) return

        speechManager.stopListening()
        _assistantState.value = AssistantState.Processing(query)

        // Append to chat
        val userMsg = ChatMessage(text = query, isUser = true)
        _chatMessages.value = _chatMessages.value + userMsg

        viewModelScope.launch {
            val result = commandRouter.processCommand(query)

            if (result.confirmationAction != null) {
                // Sensitive action needing explicit consent (e.g. Call or SMS)
                _assistantState.value = AssistantState.ConfirmationPending(result.confirmationAction)
                speakResponse(result.spokenResponseHindi, onComplete = null)
            } else {
                handleCompletedCommand(query, result)
            }
        }
    }

    private fun handleCompletedCommand(query: String, result: CommandResult) {
        val jarvisResponse = when (_currentLanguage.value) {
            SpeechLanguage.ENGLISH -> result.spokenResponseEnglish
            else -> result.spokenResponseHindi
        }

        val assistantMsg = ChatMessage(
            text = jarvisResponse,
            isUser = false,
            intentType = result.intentType,
            status = if (result.executionSuccess) "SUCCESS" else "FAILED"
        )
        _chatMessages.value = _chatMessages.value + assistantMsg

        // Save to Room DB if history is enabled
        if (_isHistoryEnabled.value) {
            viewModelScope.launch {
                commandDao.insertCommand(
                    CommandEntity(
                        query = query,
                        response = jarvisResponse,
                        intentType = result.intentType.label,
                        language = _currentLanguage.value.code,
                        status = if (result.executionSuccess) "COMPLETED" else "FAILED"
                    )
                )
            }
        }

        speakResponse(jarvisResponse) {
            // Check hands-free continuous loop
            if (_isHandsFreeMode.value) {
                viewModelScope.launch {
                    delay(700)
                    if (_isHandsFreeMode.value && _assistantState.value is AssistantState.Idle) {
                        startListening()
                    }
                }
            }
        }
    }

    private fun speakResponse(text: String, onComplete: (() -> Unit)? = null) {
        _assistantState.value = AssistantState.Speaking(text)
        ttsManager.speak(
            text = text,
            language = _currentLanguage.value,
            speechRate = prefs.speechRate,
            onStart = {
                _assistantState.value = AssistantState.Speaking(text)
            },
            onDone = {
                _assistantState.value = AssistantState.Idle
                onComplete?.invoke()
            },
            onError = {
                _assistantState.value = AssistantState.Idle
                onComplete?.invoke()
            }
        )
    }

    fun confirmAction(action: ConfirmationAction) {
        try {
            action.executeIntent()
            val text = "सर, आपके आदेश की पुष्टि कर दी गई है।"
            _chatMessages.value = _chatMessages.value + ChatMessage(
                text = text,
                isUser = false,
                intentType = action.intentType,
                status = "CONFIRMED"
            )

            if (_isHistoryEnabled.value) {
                viewModelScope.launch {
                    commandDao.insertCommand(
                        CommandEntity(
                            query = action.title,
                            response = text,
                            intentType = action.intentType.label,
                            language = _currentLanguage.value.code,
                            status = "CONFIRMED"
                        )
                    )
                }
            }

            speakResponse(text)
        } catch (e: Exception) {
            _assistantState.value = AssistantState.Error(
                messageHindi = "कार्रवाई निष्पादित करने में विफलता: ${e.localizedMessage}",
                messageEnglish = "Failed to execute: ${e.localizedMessage}"
            )
        }
    }

    fun dismissConfirmation() {
        val text = "आदेश रद्द कर दिया गया, सर।"
        _assistantState.value = AssistantState.Idle
        _chatMessages.value = _chatMessages.value + ChatMessage(
            text = text,
            isUser = false,
            intentType = IntentType.GENERAL,
            status = "CANCELLED"
        )
        speakResponse(text)
    }

    fun setLanguage(language: SpeechLanguage) {
        _currentLanguage.value = language
        prefs.preferredLanguageCode = language.code
    }

    fun requestEnableHandsFree() {
        if (!_isHandsFreeMode.value) {
            if (!prefs.handsFreeNoticeAcknowledged) {
                _showHandsFreeInfo.value = true
            } else {
                _isHandsFreeMode.value = true
                prefs.isHandsFreeEnabled = true
            }
        } else {
            _isHandsFreeMode.value = false
            prefs.isHandsFreeEnabled = false
            stopAll()
        }
    }

    fun acknowledgeHandsFreeNotice(enableNow: Boolean) {
        prefs.handsFreeNoticeAcknowledged = true
        _showHandsFreeInfo.value = false
        if (enableNow) {
            _isHandsFreeMode.value = true
            prefs.isHandsFreeEnabled = true
        }
    }

    fun dismissHandsFreeDialog() {
        _showHandsFreeInfo.value = false
    }

    fun openPrivacySheet() {
        _showPrivacySheet.value = true
    }

    fun dismissPrivacySheet() {
        _showPrivacySheet.value = false
    }

    fun toggleHistory(enabled: Boolean) {
        _isHistoryEnabled.value = enabled
        prefs.isHistoryEnabled = enabled
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            commandDao.clearAll()
        }
    }

    fun deleteHistoryItem(item: CommandEntity) {
        viewModelScope.launch {
            commandDao.deleteCommand(item)
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechManager.stopListening()
        ttsManager.shutdown()
    }
}
