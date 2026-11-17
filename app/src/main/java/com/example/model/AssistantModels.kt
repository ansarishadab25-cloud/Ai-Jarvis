package com.example.model

enum class IntentType(val label: String, val iconName: String) {
    ALARM("Alarm", "Alarm"),
    TIMER("Timer", "Timer"),
    OPEN_APP("Open App", "Apps"),
    SETTINGS("Settings", "Settings"),
    TIME_QUERY("Time", "Schedule"),
    DATE_QUERY("Date", "CalendarToday"),
    CALL_CONFIRM("Call", "Phone"),
    SMS_CONFIRM("SMS", "Chat"),
    WEB_SEARCH("Web Search", "Search"),
    HELP("Help", "HelpOutline"),
    GENERAL("Chat", "SmartToy"),
    ERROR("Error", "ErrorOutline")
}

data class ConfirmationAction(
    val intentType: IntentType,
    val title: String,
    val messageHindi: String,
    val messageEnglish: String,
    val recipient: String = "",
    val details: String = "",
    val executeIntent: () -> Unit
)

sealed interface AssistantState {
    data object Idle : AssistantState
    data class Listening(val rmsDb: Float = 0f, val partialText: String = "") : AssistantState
    data class Processing(val text: String) : AssistantState
    data class Speaking(val speechText: String) : AssistantState
    data class Error(val messageHindi: String, val messageEnglish: String, val isOffline: Boolean = false) : AssistantState
    data class ConfirmationPending(val action: ConfirmationAction) : AssistantState
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val intentType: IntentType = IntentType.GENERAL,
    val status: String = "SUCCESS"
)
