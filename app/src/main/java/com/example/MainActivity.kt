package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.FirestoreRepository
import com.example.data.JarvisDatabase
import com.example.data.PreferencesManager
import com.example.service.CommandRouter
import com.example.service.SpeechManager
import com.example.service.TextToSpeechManager
import com.example.ui.JarvisScreen
import com.example.ui.JarvisViewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOutUser
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(
            onAuthSuccess = {},
            modifier = Modifier.fillMaxSize()
        )
    } else {
        val uid = user.uid
        val displayName = user.displayName ?: user.email
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val credentialManager = remember(context) { CredentialManager.create(context) }

        key(uid) {
            val jarvisViewModel: JarvisViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        val firestoreRepository = FirestoreRepository(db, auth)
                        val commandDao = JarvisDatabase.getDatabase(app).commandDao()
                        JarvisViewModel(
                            speechManager = SpeechManager(app),
                            ttsManager = TextToSpeechManager(app),
                            commandRouter = CommandRouter(app),
                            prefs = PreferencesManager(app),
                            commandDao = commandDao,
                            firestoreRepository = firestoreRepository,
                            currentUserId = uid,
                            userDisplayName = displayName
                        )
                    }
                }
            )

            JarvisScreen(
                viewModel = jarvisViewModel,
                onSignOut = {
                    signOutUser(credentialManager, scope)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
