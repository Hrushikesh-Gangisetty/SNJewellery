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
import androidx.compose.ui.unit.dp

/**
 * The single activity. Compose handles navigation from M6.10, so screens
 * are composables rather than activities — one activity is the whole
 * host.
 *
 * What it shows today is the shell and nothing more: the login screen
 * lands in M6.7 and becomes the real start destination, and the theme
 * below is still stock Material 3, replaced by the token-derived one in
 * M6.2. Both are named here so nobody mistakes this for a finished
 * screen.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Stock MaterialTheme until M6.2 supplies the token-derived
            // one. Deliberately not a hand-written colour scheme — a
            // temporary palette would be a design value written outside
            // the token pipeline, which ADR-0008 forbids.
            MaterialTheme {
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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.shell_subtitle),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ShellScreenPreview() {
    MaterialTheme {
        ShellScreen()
    }
}
