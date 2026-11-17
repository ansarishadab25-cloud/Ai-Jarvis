package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.HearingDisabled
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.AssistantState
import com.example.model.SpeechLanguage
import com.example.ui.components.AudioWaveVisualizer
import com.example.ui.components.ChatHistoryList
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.HandsFreeInfoDialog
import com.example.ui.components.JarvisArcReactor
import com.example.ui.components.PrivacySettingsSheet
import com.example.ui.theme.JarvisAlertRed
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisCyanDim
import com.example.ui.theme.JarvisElectricBlue
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceVariant
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary

@Composable
fun JarvisScreen(
    viewModel: JarvisViewModel,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.stopAll()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val assistantState by viewModel.assistantState.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val rmsLevel by viewModel.rmsLevel.collectAsStateWithLifecycle()
    val partialText by viewModel.partialText.collectAsStateWithLifecycle()
    val currentLang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val isHandsFree by viewModel.isHandsFreeMode.collectAsStateWithLifecycle()
    val showHandsFreeDialog by viewModel.showHandsFreeInfo.collectAsStateWithLifecycle()
    val showPrivacySheet by viewModel.showPrivacySheet.collectAsStateWithLifecycle()
    val isHistoryEnabled by viewModel.isHistoryEnabled.collectAsStateWithLifecycle()
    val cloudHistoryState by viewModel.cloudHistoryState.collectAsStateWithLifecycle()
    val cloudSyncError by viewModel.cloudSyncError.collectAsStateWithLifecycle()

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            viewModel.startListening()
        }
    }

    var textInput by remember { mutableStateOf("") }
    var languageMenuExpanded by remember { mutableStateOf(false) }

    val isListening = assistantState is AssistantState.Listening
    val isSpeaking = assistantState is AssistantState.Speaking
    val isBusy = isListening || isSpeaking || assistantState is AssistantState.Processing

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisBackground),
        containerColor = JarvisBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top App Bar
                JarvisTopBar(
                    currentLang = currentLang,
                    isHandsFree = isHandsFree,
                    hasSyncError = cloudSyncError != null,
                    onLanguageMenuClick = { languageMenuExpanded = true },
                    onHandsFreeClick = { viewModel.requestEnableHandsFree() },
                    onPrivacyClick = { viewModel.openPrivacySheet() }
                )

                DropdownMenu(
                    expanded = languageMenuExpanded,
                    onDismissRequest = { languageMenuExpanded = false },
                    modifier = Modifier
                        .background(JarvisSurface)
                        .border(1.dp, JarvisBorder, RoundedCornerShape(8.dp))
                ) {
                    SpeechLanguage.entries.forEach { lang ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "${lang.nativeLabel} (${lang.name})",
                                    color = if (lang == currentLang) JarvisCyan else JarvisTextPrimary,
                                    fontWeight = if (lang == currentLang) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                viewModel.setLanguage(lang)
                                languageMenuExpanded = false
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Arc Reactor Centerpiece
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    JarvisArcReactor(
                        state = assistantState,
                        rmsLevel = rmsLevel,
                        onClick = {
                            if (!hasAudioPermission) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                viewModel.toggleListening()
                            }
                        }
                    )
                }

                // Audio Wave Visualizer
                AudioWaveVisualizer(
                    isListening = isListening,
                    isSpeaking = isSpeaking,
                    rmsLevel = rmsLevel,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // Assistant Status & Live Partial Text
                StatusDisplaySection(
                    state = assistantState,
                    partialText = partialText,
                    hasPermission = hasAudioPermission,
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                )

                // Clear STOP Button
                AnimatedVisibility(
                    visible = isBusy,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Button(
                        onClick = { viewModel.stopAll() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JarvisAlertRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .padding(vertical = 6.dp)
                            .testTag("clear_stop_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "रोकें / STOP",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Suggestion chips
                QuickActionChips(
                    onChipClick = { query ->
                        viewModel.processUserQuery(query)
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Chat History Stream
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF071022))
                        .border(1.dp, JarvisBorder, RoundedCornerShape(14.dp))
                        .padding(10.dp)
                ) {
                    ChatHistoryList(messages = chatMessages)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Text Input Bar fallback
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(JarvisSurfaceVariant)
                        .border(1.dp, Color(0xFF19325C), RoundedCornerShape(24.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = {
                            Text(
                                text = "यहाँ हिन्दी या Hinglish में टाइप करें...",
                                color = JarvisTextSecondary.copy(alpha = 0.6f),
                                fontSize = 13.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("command_text_input"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (textInput.isNotBlank()) {
                                    viewModel.processUserQuery(textInput)
                                    textInput = ""
                                }
                            }
                        )
                    )

                    IconButton(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                viewModel.processUserQuery(textInput)
                                textInput = ""
                            }
                        },
                        modifier = Modifier.testTag("send_command_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send Command",
                            tint = if (textInput.isNotBlank()) JarvisCyan else JarvisTextSecondary
                        )
                    }
                }
            }

            // Sensitive Confirmation Dialog (Calls / SMS)
            val pendingAction = (assistantState as? AssistantState.ConfirmationPending)?.action
            if (pendingAction != null) {
                ConfirmationDialog(
                    action = pendingAction,
                    onConfirm = { viewModel.confirmAction(pendingAction) },
                    onDismiss = { viewModel.dismissConfirmation() }
                )
            }

            // Hands-Free Info Dialog explaining Android OS background limitations
            if (showHandsFreeDialog) {
                HandsFreeInfoDialog(
                    onEnable = { viewModel.acknowledgeHandsFreeNotice(enableNow = true) },
                    onDismiss = { viewModel.dismissHandsFreeDialog() }
                )
            }

            // Privacy & Cloud History Sheet
            if (showPrivacySheet) {
                PrivacySettingsSheet(
                    userDisplayName = viewModel.userDisplayName,
                    isHistoryEnabled = isHistoryEnabled,
                    cloudHistoryState = cloudHistoryState,
                    onToggleHistory = { viewModel.toggleHistory(it) },
                    onClearAllHistory = { viewModel.clearAllHistory() },
                    onDeleteHistoryItem = { viewModel.deleteHistoryItem(it) },
                    onSignOut = {
                        viewModel.dismissPrivacySheet()
                        viewModel.stopAll()
                        onSignOut()
                    },
                    onDismiss = { viewModel.dismissPrivacySheet() }
                )
            }
        }
    }
}

@Composable
private fun JarvisTopBar(
    currentLang: SpeechLanguage,
    isHandsFree: Boolean,
    hasSyncError: Boolean,
    onLanguageMenuClick: () -> Unit,
    onHandsFreeClick: () -> Unit,
    onPrivacyClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(JarvisSurface)
            .border(1.dp, JarvisBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "SHADAB AI JARVIS",
                color = JarvisCyan,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (hasSyncError) Color(0xFFFF9100) else Color(0xFF00E676))
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (hasSyncError) "LOCAL MODE • SYNC RETRY" else "CLOUD SYNC • SECURE",
                    color = JarvisTextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Language selector button
            Surface(
                color = JarvisSurfaceVariant,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.5f)),
                modifier = Modifier
                    .clickable(onClick = onLanguageMenuClick)
                    .testTag("language_toggle_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = "Language",
                        tint = JarvisCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = currentLang.nativeLabel,
                        color = JarvisCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Hands-Free Mode chip
            Surface(
                color = if (isHandsFree) JarvisCyanDim else JarvisSurfaceVariant,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isHandsFree) JarvisCyan else Color(0xFF233B63)
                ),
                modifier = Modifier
                    .clickable(onClick = onHandsFreeClick)
                    .testTag("hands_free_toggle_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isHandsFree) Icons.Default.Hearing else Icons.Default.HearingDisabled,
                        contentDescription = "Hands-free",
                        tint = if (isHandsFree) JarvisCyan else JarvisTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isHandsFree) "HF ON" else "HF OFF",
                        color = if (isHandsFree) JarvisCyan else JarvisTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Privacy & Account Icon
            IconButton(
                onClick = onPrivacyClick,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("privacy_controls_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Privacy & History",
                    tint = JarvisTextSecondary,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusDisplaySection(
    state: AssistantState,
    partialText: String,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!hasPermission) {
            Text(
                text = "माइक्रोफ़ोन अनुमति आवश्यक है। बोलने के लिए अनुमति दें।",
                color = JarvisAlertRed,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = JarvisCyan,
                    contentColor = JarvisBackground
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("अनुमति दें (Grant Permission)", fontSize = 12.sp)
            }
            return
        }

        when (state) {
            is AssistantState.Listening -> {
                Text(
                    text = "सुन रहा हूँ... (Listening)",
                    color = JarvisCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                if (partialText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "\"$partialText\"",
                        color = JarvisTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            is AssistantState.Processing -> {
                Text(
                    text = "आदेश का विश्लेषण कर रहा हूँ... (Processing)",
                    color = JarvisElectricBlue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            is AssistantState.Speaking -> {
                Text(
                    text = "जार्विस बोल रहा है... (Speaking)",
                    color = Color(0xFF80F3FF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            is AssistantState.Error -> {
                Text(
                    text = state.messageHindi,
                    color = JarvisAlertRed,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )
            }
            else -> {
                Text(
                    text = "माइक दबाएं या बोलें: \"अलार्म लगाओ\", \"टाइमर शुरू करो\"",
                    color = JarvisTextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun QuickActionChips(
    onChipClick: (String) -> Unit
) {
    val suggestions = listOf(
        "⏰ 7 बजे का अलार्म",
        "⏳ 5 मिनट का टाइमर",
        "⚙️ सेटिंग्स खोलो",
        "🕒 समय क्या हुआ है?",
        "📱 यूट्यूब खोलो",
        "📶 वाई-फाई सेटिंग्स",
        "❓ तुम क्या कर सकते हो?"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        suggestions.forEach { suggestion ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(JarvisSurfaceVariant)
                    .border(1.dp, Color(0xFF1E3A68), RoundedCornerShape(12.dp))
                    .clickable { onChipClick(suggestion) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = suggestion,
                    color = JarvisTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
