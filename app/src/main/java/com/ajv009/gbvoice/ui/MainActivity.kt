package com.ajv009.gbvoice.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.ajv009.gbvoice.ui.log.LogScreen
import com.ajv009.gbvoice.ui.settings.SetupScreen
import com.ajv009.gbvoice.ui.speech.SpeechScreen
import com.ajv009.gbvoice.ui.webhook.HooksPage
import com.ajv009.gbvoice.ui.webhook.RoutesPage
import com.ajv009.gbvoice.ui.webhook.WebhookPage
import com.ajv009.gbvoice.ui.webhook.WebhookScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GbVoiceTheme { Home() } }
    }
}

private enum class Tab(val label: String, val title: String, val icon: ImageVector) {
    LOG("Log", "Dictation log", Icons.Filled.History),
    SPEECH("Speech", "Speech engine", Icons.Filled.GraphicEq),
    WEBHOOK("Webhook", "Webhook", Icons.Filled.CloudUpload),
    SETUP("Setup", "Setup", Icons.Filled.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Home() {
    var tab by rememberSaveable { mutableStateOf(Tab.LOG) }
    var webhookPage by rememberSaveable { mutableStateOf(WebhookPage.MAIN) }
    val inSubPage = tab == Tab.WEBHOOK && webhookPage != WebhookPage.MAIN
    BackHandler(enabled = inSubPage) { webhookPage = WebhookPage.MAIN }
    Scaffold(
        topBar = {
            TopAppBar(navigationIcon = {
                if (inSubPage) IconButton(onClick = { webhookPage = WebhookPage.MAIN }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            }, title = {
                // Small app name above the tab title, so the title isn't read as the app's name
                Column {
                    Text("GB Voice Bridge", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (tab == Tab.WEBHOOK) webhookPage.title else tab.title)
                }
            })
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach {
                    NavigationBarItem(
                        selected = tab == it,
                        onClick = { tab = it; webhookPage = WebhookPage.MAIN },
                        icon = { Icon(it.icon, contentDescription = null) },
                        label = { Text(it.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.LOG -> LogScreen(onOpenSpeech = { tab = Tab.SPEECH }, onOpenSetup = { tab = Tab.SETUP })
                Tab.SPEECH -> SpeechScreen()
                Tab.WEBHOOK -> when (webhookPage) {
                    WebhookPage.MAIN -> WebhookScreen(open = { webhookPage = it })
                    WebhookPage.HOOKS -> HooksPage()
                    WebhookPage.ROUTES -> RoutesPage(onOpenHooks = { webhookPage = WebhookPage.HOOKS })
                }
                Tab.SETUP -> SetupScreen()
            }
        }
    }
}
