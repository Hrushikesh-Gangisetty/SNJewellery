package com.snjewellery.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snjewellery.admin.domain.auth.AuthState
import com.snjewellery.admin.ui.screens.login.LoginScreen
import com.snjewellery.admin.ui.screens.root.RootViewModel
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
 * **Which screen opens is decided by the persisted session**, not by a
 * flag here. That is what makes a force-stop and relaunch land back where
 * the owner was (M6.8). The real navigation graph, and logout, arrive in
 * M6.10; this switch is still deliberately minimal.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SnTheme {
                RootContent()
            }
        }
    }
}

@Composable
private fun RootContent(viewModel: RootViewModel = hiltViewModel()) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    when (authState) {
        // Neither screen yet. Showing the login form while the stored
        // session is still being read makes a signed-in owner watch a
        // form appear and vanish, which reads as being logged out.
        is AuthState.Restoring -> RestoringIndicator()

        is AuthState.SignedIn -> ShellScreen()

        // A failed refresh lands on sign-in for now. M6.9 decides whether
        // an offline failure should keep the session and retry instead of
        // discarding it, since the owner did nothing wrong.
        is AuthState.SignedOut, is AuthState.RefreshFailed -> LoginScreen(onSignedIn = {})
    }
}

@Composable
private fun RestoringIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}
