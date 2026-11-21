package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.credentials.CredentialManager
import androidx.lifecycle.lifecycleScope
import com.example.data.firebase.attemptAutoSignIn
import com.example.data.firebase.configureAppCheck
import com.example.notifications.NotificationHelper
import com.example.ui.PulsoApp
import com.example.ui.theme.PulsoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check before rendering Compose UI or creating ViewModels.
        configureAppCheck(this, intent)
        NotificationHelper.ensureChannel(this)

        val credentialManager = CredentialManager.create(this)
        attemptAutoSignIn(
            context = this,
            credentialManager = credentialManager,
            onAuthSuccess = {},
            onUnauthenticated = {},
            scope = lifecycleScope
        )

        enableEdgeToEdge()
        setContent {
            PulsoTheme {
                PulsoApp()
            }
        }
    }
}
