package com.riz.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.riz.app.ui.MainScreen
import com.riz.app.ui.theme.RizTheme
import com.riz.app.viewmodel.FileViewModel
import com.riz.app.viewmodel.MessageViewModel
import com.riz.app.viewmodel.ViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val container = (application as RizApplication).container
            val factory = ViewModelFactory(container)

            RizTheme {
                val messageViewModel: MessageViewModel = viewModel(factory = factory)
                val fileViewModel: FileViewModel = viewModel(factory = factory)

                MainScreen(messageViewModel, fileViewModel, container.securityRepository)
            }
        }
    }
}
