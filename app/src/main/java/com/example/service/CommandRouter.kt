package com.example.service

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import com.example.model.ConfirmationAction
import com.example.model.IntentType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

data class CommandResult(
    val intentType: IntentType,
    val spokenResponseHindi: String,
    val spokenResponseEnglish: String,
    val confirmationAction: ConfirmationAction? = null,
    val executionSuccess: Boolean = true,
    val isDirectSpokenOnly: Boolean = false
)

class CommandRouter(private val context: Context) {

    fun processCommand(query: String): CommandResult {
        val trimmed = query.trim()
        val lower = trimmed.lowercase(Locale.ROOT)

        // 1. Check for Alarm commands
        if (isAlarmQuery(lower)) {
            return handleAlarmCommand(lower, trimmed)
        }

        // 2. Check for Timer commands
        if (isTimerQuery(lower)) {
            return handleTimerCommand(lower, trimmed)
        }

        // 3. Check for Time / Date queries
        if (isTimeOrDateQuery(lower)) {
            return handleTimeOrDateCommand(lower)
        }

        // 4. Check for Settings commands
        if (isSettingsQuery(lower)) {
            return handleSettingsCommand(lower)
        }

        // 5. Check for Call (Sensitive - requires user confirmation)
        if (isCallQuery(lower)) {
            return handleCallCommand(lower, trimmed)
        }

        // 6. Check for SMS / Message (Sensitive - requires user confirmation)
        if (isMessageQuery(lower)) {
            return handleMessageCommand(lower, trimmed)
        }

        // 7. Check for Installed Apps
        if (isOpenAppQuery(lower)) {
            val appResult = handleOpenAppCommand(lower, trimmed)
            if (appResult != null) return appResult
        }

        // 8. Check for Web Search
        if (isSearchQuery(lower)) {
            return handleSearchCommand(lower, trimmed)
        }

        // 9. Check for Help / Identity
        if (isIdentityOrHelpQuery(lower)) {
            return handleIdentityHelpCommand(lower)
        }

        // 10. Fallback: Search on Google with user query
        return handleGenericFallback(trimmed)
    }

    private fun isAlarmQuery(text: String): Boolean {
        return text.contains("अलार्म") ||
                text.contains("alarm") ||
                text.contains("wake me up") ||
                text.contains("jaga dena") ||
                (text.contains("baje") && text.contains("lagao"))
    }

    private fun handleAlarmCommand(lower: String, original: String): CommandResult {
        // Extract hour and minutes
        var hour = 7
        var minutes = 0
        var isPm = lower.contains("pm") || lower.contains("शाम") || lower.contains("रात")
        val isAm = lower.contains("am") || lower.contains("सुबह")

        // Regex for HH:MM or single number
        val timePattern = Pattern.compile("(\\d{1,2})(?::(\\d{2}))?")
        val matcher = timePattern.matcher(lower)
        if (matcher.find()) {
            hour = matcher.group(1)?.toIntOrNull() ?: 7
            minutes = matcher.group(2)?.toIntOrNull() ?: 0
        }

        if (isPm && hour < 12) hour += 12
        if (isAm && hour == 12) hour = 0

        val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", hour, minutes)

        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minutes)
                putExtra(AlarmClock.EXTRA_MESSAGE, "Shadab AI Jarvis Alarm")
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                CommandResult(
                    intentType = IntentType.ALARM,
                    spokenResponseHindi = "सर, मैंने $formattedTime बजे का अलार्म सेट कर दिया है।",
                    spokenResponseEnglish = "Sir, I have set your alarm for $formattedTime."
                )
            } else {
                CommandResult(
                    intentType = IntentType.ALARM,
                    spokenResponseHindi = "क्षमा करें सर, इस डिवाइस पर क्लॉक ऐप अलार्म स्वीकार नहीं कर रहा है।",
                    spokenResponseEnglish = "Clock app does not support alarm intent on this device.",
                    executionSuccess = false
                )
            }
        } catch (e: Exception) {
            CommandResult(
                intentType = IntentType.ALARM,
                spokenResponseHindi = "अलार्म सेट करने में समस्या आई।",
                spokenResponseEnglish = "Failed to set alarm: ${e.localizedMessage}",
                executionSuccess = false
            )
        }
    }

    private fun isTimerQuery(text: String): Boolean {
        return text.contains("टाइमर") ||
                text.contains("timer") ||
                (text.contains("minute") && text.contains("lagao")) ||
                (text.contains("second") && text.contains("lagao"))
    }

    private fun handleTimerCommand(lower: String, original: String): CommandResult {
        var durationSeconds = 300 // default 5 minutes
        val numberMatcher = Pattern.compile("(\\d+)").matcher(lower)
        var parsedNumber = 5
        if (numberMatcher.find()) {
            parsedNumber = numberMatcher.group(1)?.toIntOrNull() ?: 5
        }

        durationSeconds = if (lower.contains("second") || lower.contains("सेकंड")) {
            parsedNumber
        } else if (lower.contains("hour") || lower.contains("घंटे") || lower.contains("ghanta")) {
            parsedNumber * 3600
        } else {
            parsedNumber * 60 // default minutes
        }

        val label = if (durationSeconds >= 60) "${durationSeconds / 60} मिनट" else "$durationSeconds सेकंड"

        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, durationSeconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, "Jarvis Timer ($label)")
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                CommandResult(
                    intentType = IntentType.TIMER,
                    spokenResponseHindi = "सर, $label का टाइमर शुरू कर दिया गया है।",
                    spokenResponseEnglish = "Sir, started a timer for $label."
                )
            } else {
                CommandResult(
                    intentType = IntentType.TIMER,
                    spokenResponseHindi = "सर, आपके डिवाइस का क्लॉक ऐप सीधे टाइमर सपोर्ट नहीं करता।",
                    spokenResponseEnglish = "Clock app does not support timer intent on this device.",
                    executionSuccess = false
                )
            }
        } catch (e: Exception) {
            CommandResult(
                intentType = IntentType.TIMER,
                spokenResponseHindi = "टाइमर लगाने में समस्या आई।",
                spokenResponseEnglish = "Failed to set timer: ${e.localizedMessage}",
                executionSuccess = false
            )
        }
    }

    private fun isTimeOrDateQuery(text: String): Boolean {
        return text.contains("समय") ||
                text.contains("time") ||
                text.contains("baje") ||
                text.contains("तारीख") ||
                text.contains("date") ||
                text.contains("tareekh") ||
                text.contains("दिन") ||
                text.contains("day")
    }

    private fun handleTimeOrDateCommand(lower: String): CommandResult {
        val now = Date()
        val isDate = lower.contains("तारीख") || lower.contains("date") || lower.contains("tareekh") || lower.contains("दिन") || lower.contains("day")

        if (isDate) {
            val dateFmtHindi = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("hi", "IN")).format(now)
            val dateFmtEnglish = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.ENGLISH).format(now)
            return CommandResult(
                intentType = IntentType.DATE_QUERY,
                spokenResponseHindi = "सर, आज $dateFmtHindi है।",
                spokenResponseEnglish = "Sir, today is $dateFmtEnglish.",
                isDirectSpokenOnly = true
            )
        } else {
            val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now)
            return CommandResult(
                intentType = IntentType.TIME_QUERY,
                spokenResponseHindi = "सर, अभी समय $timeFmt हुआ है।",
                spokenResponseEnglish = "Sir, the current time is $timeFmt.",
                isDirectSpokenOnly = true
            )
        }
    }

    private fun isSettingsQuery(text: String): Boolean {
        return text.contains("सेटिंग") ||
                text.contains("settings") ||
                text.contains("setting") ||
                text.contains("वाई-फाई") ||
                text.contains("wifi") ||
                text.contains("bluetooth") ||
                text.contains("ब्लूटूथ") ||
                text.contains("battery") ||
                text.contains("बैटरी") ||
                text.contains("display") ||
                text.contains("डिस्प्ले")
    }

    private fun handleSettingsCommand(lower: String): CommandResult {
        var action = Settings.ACTION_SETTINGS
        var labelHindi = "फ़ोन"
        var labelEng = "System"

        when {
            lower.contains("wifi") || lower.contains("वाई-फाई") -> {
                action = Settings.ACTION_WIFI_SETTINGS
                labelHindi = "वाई-फाई"
                labelEng = "Wi-Fi"
            }
            lower.contains("bluetooth") || lower.contains("ब्लूटूथ") -> {
                action = Settings.ACTION_BLUETOOTH_SETTINGS
                labelHindi = "ब्लूटूथ"
                labelEng = "Bluetooth"
            }
            lower.contains("battery") || lower.contains("बैटरी") -> {
                action = Settings.ACTION_BATTERY_SAVER_SETTINGS
                labelHindi = "बैटरी"
                labelEng = "Battery"
            }
            lower.contains("display") || lower.contains("डिस्प्ले") -> {
                action = Settings.ACTION_DISPLAY_SETTINGS
                labelHindi = "डिस्प्ले"
                labelEng = "Display"
            }
            lower.contains("sound") || lower.contains("आवाज़") || lower.contains("volume") -> {
                action = Settings.ACTION_SOUND_SETTINGS
                labelHindi = "साउंड"
                labelEng = "Sound"
            }
        }

        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CommandResult(
                intentType = IntentType.SETTINGS,
                spokenResponseHindi = "सर, $labelHindi सेटिंग्स खोली जा रही हैं।",
                spokenResponseEnglish = "Sir, opening $labelEng settings."
            )
        } catch (e: Exception) {
            CommandResult(
                intentType = IntentType.SETTINGS,
                spokenResponseHindi = "सेटिंग्स खोलने में त्रुटि हुई।",
                spokenResponseEnglish = "Could not open settings: ${e.localizedMessage}",
                executionSuccess = false
            )
        }
    }

    private fun isCallQuery(text: String): Boolean {
        return text.contains("कॉल") ||
                text.contains("call") ||
                text.contains("phone karo") ||
                text.contains("फोन करो") ||
                text.contains("dial")
    }

    private fun handleCallCommand(lower: String, original: String): CommandResult {
        // Extract phone number or name
        val numberPattern = Pattern.compile("(\\+?\\d[\\d\\s-]{3,15}\\d)")
        val matcher = numberPattern.matcher(original)
        val extractedNumber = if (matcher.find()) {
            matcher.group(1)?.replace("\\s+".toRegex(), "") ?: ""
        } else {
            // Check for name after 'call' or 'कॉल'
            original.replace("(?i)(call|कॉल करो|कॉल लगाओ|फोन करो|dial|ko call karo)".toRegex(), "").trim()
        }

        val target = if (extractedNumber.isNotBlank()) extractedNumber else "निर्दिष्ट नंबर"

        // ALWAYS require user confirmation before placing calls (safety policy)
        val confirmationAction = ConfirmationAction(
            intentType = IntentType.CALL_CONFIRM,
            title = "कॉल की पुष्टि (Confirm Call)",
            messageHindi = "क्या आप वाकई $target पर कॉल डायल करना चाहते हैं?",
            messageEnglish = "Do you want to initiate dialer for $target?",
            recipient = target,
            details = "Android Intent.ACTION_DIAL",
            executeIntent = {
                val dialIntent = if (target.any { it.isDigit() }) {
                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:$target"))
                } else {
                    Intent(Intent.ACTION_DIAL)
                }.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)
            }
        )

        return CommandResult(
            intentType = IntentType.CALL_CONFIRM,
            spokenResponseHindi = "सुरक्षा नियम: कृपया $target पर कॉल की पुष्टि करें।",
            spokenResponseEnglish = "Confirmation required: Please confirm call to $target.",
            confirmationAction = confirmationAction
        )
    }

    private fun isMessageQuery(text: String): Boolean {
        return text.contains("मैसेज") ||
                text.contains("message") ||
                text.contains("sms") ||
                text.contains("एसएमएस")
    }

    private fun handleMessageCommand(lower: String, original: String): CommandResult {
        val numberPattern = Pattern.compile("(\\+?\\d[\\d\\s-]{3,15}\\d)")
        val matcher = numberPattern.matcher(original)
        val targetNumber = if (matcher.find()) {
            matcher.group(1)?.replace("\\s+".toRegex(), "") ?: ""
        } else {
            ""
        }

        val messageText = original.replace("(?i)(मैसेज भेजो|send message to|sms karo|message karo|send sms)".toRegex(), "")
            .replace(targetNumber, "").trim()

        val recipientLabel = if (targetNumber.isNotBlank()) targetNumber else "प्राप्तकर्ता (Recipient)"

        // ALWAYS require confirmation for SMS
        val confirmationAction = ConfirmationAction(
            intentType = IntentType.SMS_CONFIRM,
            title = "मैसेज भेजने की पुष्टि (Confirm SMS)",
            messageHindi = "क्या आप $recipientLabel को यह संदेश भेजना चाहते हैं:\n\"${messageText.ifBlank { "नया संदेश" }}\"?",
            messageEnglish = "Send message to $recipientLabel:\n\"${messageText.ifBlank { "New message" }}\"?",
            recipient = recipientLabel,
            details = messageText,
            executeIntent = {
                val uri = if (targetNumber.isNotBlank()) Uri.parse("smsto:$targetNumber") else Uri.parse("smsto:")
                val smsIntent = Intent(Intent.ACTION_SENDTO, uri).apply {
                    if (messageText.isNotBlank()) {
                        putExtra("sms_body", messageText)
                    }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(smsIntent)
            }
        )

        return CommandResult(
            intentType = IntentType.SMS_CONFIRM,
            spokenResponseHindi = "संदेश सुरक्षा: कृपया संदेश भेजने की पुष्टि करें।",
            spokenResponseEnglish = "Confirmation required: Please confirm before sending SMS.",
            confirmationAction = confirmationAction
        )
    }

    private fun isOpenAppQuery(text: String): Boolean {
        return text.contains("खोलो") ||
                text.contains("open") ||
                text.contains("chalao") ||
                text.contains("chalu karo") ||
                text.contains("शुरू करो") ||
                text.contains("youtube") ||
                text.contains("यूट्यूब") ||
                text.contains("whatsapp") ||
                text.contains("व्हाट्सएप") ||
                text.contains("camera") ||
                text.contains("कैमरा") ||
                text.contains("calculator") ||
                text.contains("कैलकुलेटर") ||
                text.contains("chrome") ||
                text.contains("क्रोम") ||
                text.contains("maps") ||
                text.contains("मैप्स")
    }

    private fun handleOpenAppCommand(lower: String, original: String): CommandResult? {
        val appKeywords = listOf(
            Triple(listOf("youtube", "यूट्यूब", "yt"), "com.google.android.youtube", "यूट्यूब (YouTube)"),
            Triple(listOf("whatsapp", "व्हाट्सएप", "wa"), "com.whatsapp", "व्हाट्सएप (WhatsApp)"),
            Triple(listOf("chrome", "क्रोम", "browser", "इंटरनेट"), "com.android.chrome", "गूगल क्रोम (Chrome)"),
            Triple(listOf("camera", "कैमरा", "photo"), "android.media.action.IMAGE_CAPTURE", "कैमरा (Camera)"),
            Triple(listOf("maps", "मैप्स", "नक्शा"), "com.google.android.apps.maps", "गूगल मैप्स (Google Maps)"),
            Triple(listOf("calculator", "कैलकुलेटर", "hisab"), "com.google.android.calculator", "कैलकुलेटर (Calculator)"),
            Triple(listOf("gmail", "जीमेल", "mail", "ईमेल"), "com.google.android.gm", "जीमेल (Gmail)"),
            Triple(listOf("clock", "घड़ी"), "com.google.android.deskclock", "क्लॉक (Clock)"),
            Triple(listOf("contacts", "संपर्क"), "com.google.android.contacts", "कॉन्टैक्ट्स (Contacts)")
        )

        for ((synonyms, pkgOrAction, displayName) in appKeywords) {
            if (synonyms.any { lower.contains(it) }) {
                // If camera action
                if (pkgOrAction == "android.media.action.IMAGE_CAPTURE") {
                    val camIntent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    return try {
                        context.startActivity(camIntent)
                        CommandResult(
                            intentType = IntentType.OPEN_APP,
                            spokenResponseHindi = "सर, कैमरा खोला जा रहा है।",
                            spokenResponseEnglish = "Opening Camera, sir."
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                // Check package
                val launchIntent = context.packageManager.getLaunchIntentForPackage(pkgOrAction)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return CommandResult(
                        intentType = IntentType.OPEN_APP,
                        spokenResponseHindi = "सर, $displayName खोला जा रहा है।",
                        spokenResponseEnglish = "Opening $displayName, sir."
                    )
                }
            }
        }

        // Generic search through installed applications
        try {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val cleanQuery = original.replace("(?i)(open|खोलो|chalao|kholo|app)".toRegex(), "").trim().lowercase(Locale.ROOT)

            if (cleanQuery.length >= 3) {
                for (app in packages) {
                    val appLabel = pm.getApplicationLabel(app).toString().lowercase(Locale.ROOT)
                    if (appLabel.contains(cleanQuery) || cleanQuery.contains(appLabel)) {
                        val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launchIntent)
                            val label = pm.getApplicationLabel(app).toString()
                            return CommandResult(
                                intentType = IntentType.OPEN_APP,
                                spokenResponseHindi = "सर, $label ऐप खोला जा रहा है।",
                                spokenResponseEnglish = "Opening $label, sir."
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return null
    }

    private fun isSearchQuery(text: String): Boolean {
        return text.contains("सर्च") ||
                text.contains("search") ||
                text.contains("google pe") ||
                text.contains("गूगल पर") ||
                text.contains("dhundo") ||
                text.contains("ढूंढो") ||
                text.contains("खोजो")
    }

    private fun handleSearchCommand(lower: String, original: String): CommandResult {
        val query = original.replace("(?i)(search for|search|सर्च करो|गूगल पर ढूंढो|google pe dhundo|dhundo|खोजो)".toRegex(), "").trim()
        val finalQuery = if (query.isNotBlank()) query else original

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(finalQuery)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            CommandResult(
                intentType = IntentType.WEB_SEARCH,
                spokenResponseHindi = "सर, गूगल पर '$finalQuery' खोजा जा रहा है।",
                spokenResponseEnglish = "Searching Google for '$finalQuery', sir."
            )
        } catch (e: Exception) {
            CommandResult(
                intentType = IntentType.WEB_SEARCH,
                spokenResponseHindi = "सर्च खोलने में समस्या आई।",
                spokenResponseEnglish = "Could not open browser search: ${e.localizedMessage}",
                executionSuccess = false
            )
        }
    }

    private fun isIdentityOrHelpQuery(text: String): Boolean {
        return text.contains("तुम कौन हो") ||
                text.contains("tum kaun ho") ||
                text.contains("who are you") ||
                text.contains("तुम्हारा नाम") ||
                text.contains("naam kya hai") ||
                text.contains("help") ||
                text.contains("मदद") ||
                text.contains("kya kar sakte ho") ||
                text.contains("what can you do") ||
                text.contains("नमस्ते") ||
                text.contains("hello jarvis") ||
                text.contains("hi jarvis")
    }

    private fun handleIdentityHelpCommand(lower: String): CommandResult {
        if (lower.contains("help") || lower.contains("मदद") || lower.contains("kya kar sakte ho") || lower.contains("what can you do")) {
            return CommandResult(
                intentType = IntentType.HELP,
                spokenResponseHindi = "सर, मैं अलार्म लगा सकता हूँ, टाइमर सेट कर सकता हूँ, ऐप्स और फ़ोन सेटिंग्स खोल सकता हूँ, और आपके आदेश पर कॉल या मैसेज की पुष्टि कर सकता हूँ।",
                spokenResponseEnglish = "Sir, I can set alarms, start timers, open apps, access device settings, and prepare calls or messages with safety confirmation."
            )
        }

        return CommandResult(
            intentType = IntentType.GENERAL,
            spokenResponseHindi = "नमस्ते सर! मैं शादाब एआई जार्विस हूँ, आपका निजी वॉयस असिस्टेंट। आज मैं आपकी क्या सेवा करूँ?",
            spokenResponseEnglish = "Greetings, sir! I am Shadab AI Jarvis, your native voice assistant. How may I assist you today?"
        )
    }

    private fun handleGenericFallback(query: String): CommandResult {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            CommandResult(
                intentType = IntentType.WEB_SEARCH,
                spokenResponseHindi = "सर, इसके लिए मैं वेब सर्च खोल रहा हूँ: '$query'।",
                spokenResponseEnglish = "Sir, searching online for '$query'."
            )
        } catch (e: Exception) {
            CommandResult(
                intentType = IntentType.GENERAL,
                spokenResponseHindi = "माफ़ कीजिए सर, मैं इस आदेश को समझ नहीं पाया। आप 'अलार्म लगाओ', 'टाइमर शुरू करो' या 'सेटिंग्स खोलो' बोल सकते हैं।",
                spokenResponseEnglish = "Pardon sir, I did not recognize that command. Try saying 'Set alarm', 'Start timer', or 'Open settings'.",
                executionSuccess = false
            )
        }
    }
}
