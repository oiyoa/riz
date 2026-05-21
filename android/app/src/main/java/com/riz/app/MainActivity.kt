package com.riz.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.riz.app.ui.MainScreen
import com.riz.app.ui.theme.RizTheme
import com.riz.app.viewmodel.FileViewModel
import com.riz.app.viewmodel.MessageViewModel
import com.riz.app.viewmodel.ViewModelFactory

// FragmentActivity is required for AndroidX BiometricPrompt; ComponentActivity
// alone won't satisfy the constructor. FragmentActivity extends ComponentActivity
// so nothing else changes about how the activity behaves.
class MainActivity : FragmentActivity() {
    private val container by lazy { (application as RizApplication).container }
    private val factory by lazy { ViewModelFactory(container) }
    private val messageViewModel: MessageViewModel by viewModels { factory }
    private val fileViewModel: FileViewModel by viewModels { factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Plaintext (and the password sheet) must not appear in recents
        // thumbnails or be captured by other apps' screenshot APIs.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )

        handleShareIntent(intent)

        setContent {
            RizTheme {
                MainScreen(messageViewModel, fileViewModel, container.securityRepository)
            }
        }

        // Posts to the end of Main's message queue — runs after the first frame is committed,
        // so background init can't starve the first composition for CPU.
        window.decorView.post { (application as RizApplication).startPostFrameWarmup() }
    }

    // singleTask launch mode routes hot shares here instead of starting a new activity.
    // Without setIntent(), subsequent getIntent() calls would still return the launch intent.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val type = intent.type.orEmpty()
                if (type.startsWith("text/")) {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
                    if (text.isNotBlank()) messageViewModel.ingestSharedText(text)
                } else {
                    val uri =
                        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                            ?: return
                    fileViewModel.ingestSharedFiles(listOf(uri))
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris =
                    IntentCompat.getParcelableArrayListExtra(
                        intent,
                        Intent.EXTRA_STREAM,
                        Uri::class.java,
                    ) ?: return
                if (uris.isNotEmpty()) fileViewModel.ingestSharedFiles(uris)
            }
        }
    }
}
