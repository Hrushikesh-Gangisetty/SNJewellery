package com.snjewellery.admin.ui.screens.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
 * The shell. Replaced as the start destination by the login screen in
 * M6.7 — it is here to say plainly that the app is not finished, and to
 * report whether this build was given backend credentials.
 *
 * Split in two on purpose, and this is the pattern every screen in the
 * app follows:
 *
 * - the **stateful** composable resolves the view model and nothing else;
 * - the **stateless** one takes plain values and is what previews and
 *   tests render.
 *
 * Without the split, a preview needs Hilt and a screen cannot be
 * rendered without an application graph — which is how Compose previews
 * quietly stop working in an app of any size.
 */
@Composable
fun ShellScreen(
    modifier: Modifier = Modifier,
    viewModel: ShellViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShellScreen(uiState = uiState, modifier = modifier)
}

@Composable
internal fun ShellScreen(
    uiState: ShellUiState,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
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
            // displayLarge is Cormorant Garamond, so this line doubles as
            // the check that the bundled serif loaded.
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = stringResource(R.string.shell_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (uiState.configured) {
                Text(
                    text = stringResource(R.string.shell_backend_ready),
                    style = MaterialTheme.snTextStyles.label,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                // `error`, not the accent: this is a build that cannot
                // sign in, and it must not look like ordinary status.
                Text(
                    text = stringResource(
                        R.string.shell_backend_missing,
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
private fun ShellScreenReadyPreview() {
    SnTheme {
        ShellScreen(uiState = ShellUiState(configured = true))
    }
}

@PreviewLightDark
@Composable
private fun ShellScreenUnconfiguredPreview() {
    SnTheme {
        ShellScreen(
            uiState = ShellUiState(
                configured = false,
                missingConfig = listOf("SUPABASE_URL", "SUPABASE_ANON_KEY"),
            ),
        )
    }
}
