package com.snjewellery.admin.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snjewellery.admin.R
import com.snjewellery.admin.ui.theme.SnTheme
import com.snjewellery.admin.ui.theme.Tokens
import com.snjewellery.admin.ui.theme.snTextStyles

/**
 * The app's start destination once signed in.
 *
 * Carries the top bar, which is where **Sign out** lives — the one
 * control M6.10 owes. Signing out does not navigate: it clears the
 * session, and the state above the graph swaps in the login screen. See
 * `AdminNavHost`.
 *
 * The body still says the dashboard is unbuilt. M6.11 replaces that with
 * the four live metrics; showing a plausible zero in the meantime would
 * be a lie the owner could act on.
 */
@Composable
fun DashboardScreen(
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardScreen(uiState = uiState, onSignOut = onSignOut, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardScreen(
    uiState: DashboardUiState,
    modifier: Modifier = Modifier,
    onSignOut: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    TextButton(onClick = onSignOut) {
                        Text(
                            text = stringResource(R.string.dashboard_sign_out),
                            style = MaterialTheme.snTextStyles.label,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Tokens.Space.s6),
            verticalArrangement = Arrangement.spacedBy(
                Tokens.Space.s2,
                Alignment.CenterVertically,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.dashboard_pending),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (uiState.configured) {
                Text(
                    text = stringResource(R.string.dashboard_backend_ready),
                    style = MaterialTheme.snTextStyles.label,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                // `error`, not the accent: this is a build that cannot
                // reach the backend, and it must not look like status.
                Text(
                    text = stringResource(
                        R.string.dashboard_backend_missing,
                        uiState.missingConfig.joinToString(", "),
                    ),
                    style = MaterialTheme.snTextStyles.label,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun DashboardReadyPreview() {
    SnTheme {
        DashboardScreen(uiState = DashboardUiState(configured = true))
    }
}

@PreviewLightDark
@Composable
private fun DashboardUnconfiguredPreview() {
    SnTheme {
        DashboardScreen(
            uiState = DashboardUiState(
                configured = false,
                missingConfig = listOf("SUPABASE_URL", "SUPABASE_ANON_KEY"),
            ),
        )
    }
}
