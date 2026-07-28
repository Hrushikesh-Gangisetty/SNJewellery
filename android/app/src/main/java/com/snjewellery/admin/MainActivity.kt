package com.snjewellery.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.snjewellery.admin.ui.screens.shell.ShellScreen
import com.snjewellery.admin.ui.theme.SnTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single activity. Compose handles navigation from M6.10, so screens
 * are composables rather than activities — one activity hosts them all.
 *
 * `@AndroidEntryPoint` is what lets composables below it resolve view
 * models from the graph; without it `hiltViewModel()` fails at runtime.
 *
 * It stays this thin permanently. Everything the app does belongs to a
 * screen, and the navigation host replaces this call in M6.10.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SnTheme {
                ShellScreen()
            }
        }
    }
}
