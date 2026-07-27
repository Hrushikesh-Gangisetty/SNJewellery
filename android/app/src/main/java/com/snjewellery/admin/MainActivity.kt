package com.snjewellery.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.snjewellery.admin.ui.theme.SnTheme
import com.snjewellery.admin.ui.theme.Tokens
import com.snjewellery.admin.ui.theme.snTextStyles

/**
 * The single activity. Compose handles navigation from M6.10, so screens
 * are composables rather than activities — one activity is the whole
 * host.
 *
 * What it shows today is the shell and nothing more: the login screen
 * lands in M6.7 and becomes the real start destination. It is named here
 * so nobody mistakes it for a finished screen.
 */
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

@Composable
private fun ShellScreen(modifier: Modifier = Modifier) {
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
            // displayLarge is Cormorant Garamond at display-l, so this
            // line is also the check that the bundled serif loaded.
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.shell_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.shell_theme_check),
                style = MaterialTheme.snTextStyles.label,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Both schemes, side by side. M6.2's acceptance criterion is that light
 * and dark each render correctly, and a preview that only ever shows one
 * of them cannot demonstrate that.
 */
@PreviewLightDark
@Preview(showBackground = true)
@Composable
private fun ShellScreenPreview() {
    SnTheme {
        ShellScreen()
    }
}
