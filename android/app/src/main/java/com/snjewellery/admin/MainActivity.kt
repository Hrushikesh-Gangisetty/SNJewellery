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
import com.snjewellery.admin.domain.auth.SessionState
import com.snjewellery.admin.ui.screens.access.AccessScreen
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
 * the owner was (M6.8), and from M6.9 it also decides whether being
 * signed in is enough — only [SessionState.Admin] reaches the app. The
 * real navigation graph arrives in M6.10; this switch is still
 * deliberately minimal.
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
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()

    when (val state = sessionState) {
        // Neither screen yet. Showing the login form while the stored
        // session is still being read makes a signed-in owner watch a
        // form appear and vanish, which reads as being logged out. The
        // role check that follows gets the same treatment for the same
        // reason — it is a round trip, not an instant answer.
        is SessionState.Restoring, is SessionState.VerifyingAccess -> WaitingIndicator()

        is SessionState.Admin -> ShellScreen()

        // Signing in is not the same as being allowed in. Both blocked
        // states explain themselves and offer a way out — M6.9.
        is SessionState.Blocked -> AccessScreen(
            state = state,
            onRetry = viewModel::retryAccessCheck,
            onSignOut = viewModel::signOut,
        )

        // Navigation on success is the session flow's job, not the login
        // screen's: the same transition has to happen when a stored
        // session is restored, and one path is one thing to get right.
        is SessionState.SignedOut -> LoginScreen(onSignedIn = {})
    }
}

@Composable
private fun WaitingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}
