package com.example.model

enum class SpeechLanguage(
    val code: String,
    val nativeLabel: String,
    val localeTag: String
) {
    HINDI("hi-IN", "हिन्दी", "hi-IN"),
    HINGLISH("hi-en", "Hinglish", "hi-IN"),
    ENGLISH("en-IN", "English", "en-IN");

    companion object {
        fun fromCode(code: String): SpeechLanguage {
            return entries.find { it.code == code } ?: HINDI
        }
    }
}
