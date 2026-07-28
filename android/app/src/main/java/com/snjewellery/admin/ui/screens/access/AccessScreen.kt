package com.snjewellery.admin.ui.screens.access

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.snjewellery.admin.R
import com.snjewellery.admin.domain.auth.RefusalReason
import com.snjewellery.admin.domain.auth.SessionState
import com.snjewellery.admin.ui.theme.SnTheme
import com.snjewellery.admin.ui.theme.Tokens

/**
 * Shown when a signed-in account may not administer the catalogue, or
 * when that could not be established.
 *
 * ── Why one screen for two outcomes ──────────────────────────────────
 * They read the same to the person holding the phone — "I signed in and
 * I am not where I expected to be" — and differ only in the sentence
 * explaining why and in whether trying again could help. Two screens
 * would be two layouts to keep matching.
 *
 * ── Why both actions exist ───────────────────────────────────────────
 * **Try again** because neither state is necessarily permanent: an
 * administrator can grant the role while this screen is open, and a
 * connection can come back. Re-checking is cheaper than a re-login, and
 * it is what makes being granted access feel immediate.
 *
 * **Sign out** because M6.8 persists the session. Without it, someone who
 * signed in with the wrong account meets this screen on every relaunch
 * with nothing to press — the blank-wall failure this task exists to
 * prevent, just with words on it.
 *
 * There is deliberately no way past it. The gate is not the security
 * boundary — RLS is, and it holds regardless (ADR-0004) — but letting a
 * refused account into screens whose every action silently fails would be
 * a worse explanation than none.
 */
@Composable
internal fun AccessScreen(
    state: SessionState.Blocked,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(Tokens.Space.s6),
            verticalArrangement = Arrangement.spacedBy(
                Tokens.Space.s4,
                Alignment.CenterVertically,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(state.titleRes()),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Text(
                text = state.explanation(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Tokens.Layout.touchTarget),
            ) {
                Text(stringResource(R.string.access_retry))
            }

            TextButton(
                onClick = onSignOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Tokens.Layout.touchTarget),
            ) {
                Text(stringResource(R.string.access_sign_out))
            }
        }
    }
}

private fun SessionState.Blocked.titleRes(): Int = when (this) {
    is SessionState.Refused -> R.string.access_refused_title
    is SessionState.AccessUnavailable -> R.string.access_unavailable_title
}

/**
 * The body copy. A composable rather than a resource id because the
 * unavailable case interpolates a detail that is sometimes absent.
 */
@Composable
private fun SessionState.Blocked.explanation(): String = when (this) {
    is SessionState.Refused -> stringResource(
        when (reason) {
            RefusalReason.NotAnAdmin -> R.string.access_refused_not_admin
            RefusalReason.NoProfile -> R.string.access_refused_no_profile
        },
    )

    is SessionState.AccessUnavailable -> when {
        offline -> stringResource(R.string.access_unavailable_offline)
        else -> stringResource(
            R.string.access_unavailable_detail,
            detail ?: stringResource(R.string.access_unavailable_no_detail),
        )
    }
}

@PreviewLightDark
@Composable
private fun AccessRefusedNotAdminPreview() {
    SnTheme {
        AccessScreen(
            state = SessionState.Refused(RefusalReason.NotAnAdmin),
            onRetry = {},
            onSignOut = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun AccessRefusedNoProfilePreview() {
    SnTheme {
        AccessScreen(
            state = SessionState.Refused(RefusalReason.NoProfile),
            onRetry = {},
            onSignOut = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun AccessUnavailableOfflinePreview() {
    SnTheme {
        AccessScreen(
            state = SessionState.AccessUnavailable(offline = true, detail = null),
            onRetry = {},
            onSignOut = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun AccessUnavailableDetailPreview() {
    SnTheme {
        AccessScreen(
            state = SessionState.AccessUnavailable(offline = false, detail = "PGRST301"),
            onRetry = {},
            onSignOut = {},
        )
    }
}
