package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CloudCommandRecord
import com.example.data.CommandDao
import com.example.data.CommandEntity
import com.example.data.FirestoreRepository
import com.example.data.PreferencesManager
import com.example.data.UiState
import com.example.model.AssistantState
import com.example.model.ChatMessage
import com.example.model.ConfirmationAction
import com.example.model.IntentType
import com.example.model.SpeechLanguage
import com.example.service.CommandResult
import com.example.service.CommandRouter
import com.example.service.SpeechManager
import com.example.service.TextToSpeechManager
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class JarvisViewModel(
    private val speechManager: SpeechManager,
    private val ttsManager: TextToSpeechManager,
    private val commandRouter: CommandRouter,
    private val prefs: PreferencesManager,
    private val commandDao: CommandDao,
    private val firestoreRepository: FirestoreRepository,
    val currentUserId: String,
    val userDisplayName: String?
) : ViewModel() {

    private val _assistantState = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                text = "नमस्ते${userDisplayName?.let { " $it" } ?: " सर"}! मैं शादाब एआई जार्विस हूँ। माइक बटन दबाएं या मुझे बोलें: '7 बजे अलार्म लगाओ', '5 मिनट टाइमर', या 'सेटिंग्स खोलो'।",
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

    private val _cloudSyncError = MutableStateFlow<String?>(null)
    val cloudSyncError: StateFlow<String?> = _cloudSyncError.asStateFlow()

    val cloudHistoryState: StateFlow<UiState<List<CloudCommandRecord>>> =
        firestoreRepository.observeCommandHistory(currentUserId)
            .map<List<CloudCommandRecord>, UiState<List<CloudCommandRecord>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                emit(UiState.Error(rootError.message ?: "क्लाउड इतिहास लोड नहीं हो सका।"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    init {
        viewModelScope.launch {
            firestoreRepository.ensureUserProfile(
                userId = currentUserId,
                displayName = userDisplayName,
                defaultLanguage = prefs.preferredLanguageCode,
                handsFreeEnabled = prefs.isHandsFreeEnabled,
                historyEnabled = prefs.isHistoryEnabled,
                speechRate = prefs.speechRate.toDouble()
            ).onFailure { err ->
                _cloudSyncError.value = err.localizedMessage
            }
        }

        viewModelScope.launch {
            firestoreRepository.observeUserProfile(currentUserId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                }
                .collect { profile ->
                    if (profile != null) {
                        val syncedLang = SpeechLanguage.fromCode(profile.preferredLanguageCode)
                        _currentLanguage.value = syncedLang
                        prefs.preferredLanguageCode = syncedLang.code
                        _isHandsFreeMode.value = profile.handsFreeEnabled
                        prefs.isHandsFreeEnabled = profile.handsFreeEnabled
                        _isHistoryEnabled.value = profile.historyEnabled
                        prefs.isHistoryEnabled = profile.historyEnabled
                    }
                }
        }
    }

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

        val userMsg = ChatMessage(text = query, isUser = true)
        _chatMessages.value = _chatMessages.value + userMsg

        viewModelScope.launch {
            val result = commandRouter.processCommand(query)

            if (result.confirmationAction != null) {
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

        val statusStr = if (result.executionSuccess) "COMPLETED" else "FAILED"
        val assistantMsg = ChatMessage(
            text = jarvisResponse,
            isUser = false,
            intentType = result.intentType,
            status = statusStr
        )
        _chatMessages.value = _chatMessages.value + assistantMsg

        if (_isHistoryEnabled.value) {
            saveHistoryEntry(
                query = query,
                response = jarvisResponse,
                intentType = result.intentType.label,
                language = _currentLanguage.value.code,
                status = statusStr
            )
        }

        speakResponse(jarvisResponse) {
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

    private fun saveHistoryEntry(
        query: String,
        response: String,
        intentType: String,
        language: String,
        status: String
    ) {
        viewModelScope.launch {
            commandDao.insertCommand(
                CommandEntity(
                    query = query,
                    response = response,
                    intentType = intentType,
                    language = language,
                    status = status
                )
            )
            firestoreRepository.addCommandRecord(
                userId = currentUserId,
                query = query,
                response = response,
                intentType = intentType,
                language = language,
                status = status
            ).onFailure { err ->
                _cloudSyncError.value = err.localizedMessage
            }.onSuccess {
                _cloudSyncError.value = null
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
                saveHistoryEntry(
                    query = action.title,
                    response = text,
                    intentType = action.intentType.label,
                    language = _currentLanguage.value.code,
                    status = "CONFIRMED"
                )
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
        viewModelScope.launch {
            firestoreRepository.updatePreferences(
                userId = currentUserId,
                preferredLanguageCode = language.code
            )
        }
    }

    fun requestEnableHandsFree() {
        if (!_isHandsFreeMode.value) {
            if (!prefs.handsFreeNoticeAcknowledged) {
                _showHandsFreeInfo.value = true
            } else {
                _isHandsFreeMode.value = true
                prefs.isHandsFreeEnabled = true
                viewModelScope.launch {
                    firestoreRepository.updatePreferences(
                        userId = currentUserId,
                        handsFreeEnabled = true
                    )
                }
            }
        } else {
            _isHandsFreeMode.value = false
            prefs.isHandsFreeEnabled = false
            stopAll()
            viewModelScope.launch {
                firestoreRepository.updatePreferences(
                    userId = currentUserId,
                    handsFreeEnabled = false
                )
            }
        }
    }

    fun acknowledgeHandsFreeNotice(enableNow: Boolean) {
        prefs.handsFreeNoticeAcknowledged = true
        _showHandsFreeInfo.value = false
        if (enableNow) {
            _isHandsFreeMode.value = true
            prefs.isHandsFreeEnabled = true
            viewModelScope.launch {
                firestoreRepository.updatePreferences(
                    userId = currentUserId,
                    handsFreeEnabled = true
                )
            }
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
        viewModelScope.launch {
            firestoreRepository.updatePreferences(
                userId = currentUserId,
                historyEnabled = enabled
            )
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            commandDao.clearAll()
            firestoreRepository.clearAllCommands(currentUserId)
                .onFailure { err -> _cloudSyncError.value = err.localizedMessage }
        }
    }

    fun deleteHistoryItem(item: CloudCommandRecord) {
        viewModelScope.launch {
            firestoreRepository.deleteCommandRecord(currentUserId, item.id)
                .onFailure { err -> _cloudSyncError.value = err.localizedMessage }
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechManager.stopListening()
        ttsManager.shutdown()
    }
}
