package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.TogetherGpsTheme

class MainActivity : ComponentActivity() {

    private var pendingInviteCode by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingInviteCode = extractInviteCode(intent)

        setContent {
            TogetherGpsTheme {
                AppNavigation(
                    initialInviteCode = pendingInviteCode,
                    onClearInitialInviteCode = { pendingInviteCode = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val extracted = extractInviteCode(intent)
        if (!extracted.isNullOrBlank()) {
            pendingInviteCode = extracted
        }
    }

    private fun extractInviteCode(intent: Intent?): String? {
        val data = intent?.data ?: return null
        if (data.scheme == "togethergps" && data.host == "pair") {
            return data.getQueryParameter("code")?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
        }
        return null
    }
}
