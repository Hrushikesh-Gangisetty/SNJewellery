package com.snjewellery.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.snjewellery.admin.ui.screens.login.LoginScreen
import com.snjewellery.admin.ui.screens.shell.ShellScreen
import com.snjewellery.admin.ui.theme.SnTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single activity. Compose handles navigation, so screens are
 * composables rather than activities — one activity hosts them all.
 *
 * `@AndroidEntryPoint` is what lets composables below it resolve view
 * models from the graph; without it `hiltViewModel()` fails at runtime.
 *
 * **The two-state switch below is temporary and is M6.10's job to
 * replace.** It exists only so the login screen is reachable and can be
 * tested on a device; it is not navigation. It does not survive
 * configuration change or process death deliberately — pretending
 * otherwise would hide what M6.8 has to build. Nothing here checks the
 * signed-in user's role either; that gate is M6.9.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SnTheme {
                var signedIn by remember { mutableStateOf(false) }

                if (signedIn) {
                    ShellScreen()
                } else {
                    LoginScreen(onSignedIn = { signedIn = true })
                }
            }
        }
    }
}
