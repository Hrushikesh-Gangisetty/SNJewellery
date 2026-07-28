package com.snjewellery.admin.ui.screens.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snjewellery.admin.R
import com.snjewellery.admin.domain.auth.CredentialProblem
import com.snjewellery.admin.ui.theme.SnTheme
import com.snjewellery.admin.ui.theme.Tokens
import com.snjewellery.admin.ui.theme.snTextStyles

/**
 * Sign-in. The app's start destination from M6.10.
 *
 * ── One-handed, on mobile data, between customers ────────────────────
 * The form scrolls and carries `imePadding`, so the keyboard cannot cover
 * the button — on a small phone with a 40-character email field visible,
 * it otherwise does. Email goes straight to the password field on
 * "Next", and "Done" submits, so signing in never needs a reach for the
 * button at all.
 *
 * ── Failures say different things ────────────────────────────────────
 * M6.7's actual requirement. "Wrong email or password" and "no
 * connection" lead to different actions, and the second explicitly says
 * the details were not the problem — otherwise someone retypes a correct
 * password repeatedly on a train.
 *
 * ── Show password ────────────────────────────────────────────────────
 * A worded toggle rather than an eye icon, which would mean pulling in
 * the extended Material icon set for one glyph (CLAUDE.md §3.7). It also
 * reads unambiguously, where an eye's meaning is a guess.
 */
@Composable
fun LoginScreen(
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LoginScreen(
        uiState = uiState,
        onEmailChange = viewModel::onEmailChange,
        onEmailBlur = viewModel::onEmailBlur,
        onPasswordChange = viewModel::onPasswordChange,
        onPasswordVisibilityToggle = viewModel::onPasswordVisibilityToggle,
        onSubmit = viewModel::signIn,
        onSignedIn = onSignedIn,
        modifier = modifier,
    )
}

@Composable
internal fun LoginScreen(
    uiState: LoginUiState,
    modifier: Modifier = Modifier,
    onEmailChange: (String) -> Unit = {},
    onEmailBlur: () -> Unit = {},
    onPasswordChange: (String) -> Unit = {},
    onPasswordVisibilityToggle: () -> Unit = {},
    onSubmit: () -> Unit = {},
    onSignedIn: () -> Unit = {},
) {
    val keyboard = LocalSoftwareKeyboardController.current

    // Navigation is the caller's business; M6.10 supplies the real
    // destination. Keyed on the flag so it fires once.
    if (uiState.signedIn) {
        LaunchedEffect(Unit) { onSignedIn() }
    }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(Tokens.Space.s6),
            verticalArrangement = Arrangement.spacedBy(
                Tokens.Space.s4,
                Alignment.CenterVertically,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            OutlinedTextField(
                value = uiState.email,
                onValueChange = onEmailChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (!it.isFocused) onEmailBlur() },
                enabled = !uiState.submitting,
                label = { Text(stringResource(R.string.login_email_label)) },
                isError = uiState.emailProblem != null,
                supportingText = uiState.emailProblem?.let { problem ->
                    { Text(stringResource(problem.messageRes())) }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
            )

            OutlinedTextField(
                value = uiState.password,
                onValueChange = onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.submitting,
                label = { Text(stringResource(R.string.login_password_label)) },
                isError = uiState.passwordProblem != null,
                supportingText = uiState.passwordProblem?.let { problem ->
                    { Text(stringResource(problem.messageRes())) }
                },
                singleLine = true,
                visualTransformation = if (uiState.passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    TextButton(
                        onClick = onPasswordVisibilityToggle,
                        enabled = !uiState.submitting,
                    ) {
                        Text(
                            stringResource(
                                if (uiState.passwordVisible) {
                                    R.string.login_password_hide
                                } else {
                                    R.string.login_password_show
                                },
                            ),
                            style = MaterialTheme.snTextStyles.label,
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboard?.hide()
                        onSubmit()
                    },
                ),
            )

            Button(
                onClick = {
                    keyboard?.hide()
                    onSubmit()
                },
                enabled = uiState.canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Tokens.Layout.touchTarget),
            ) {
                if (uiState.submitting) {
                    // Sized to the label's line so the button does not
                    // change height when it starts working.
                    CircularProgressIndicator(
                        modifier = Modifier.size(Tokens.Space.s5),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.login_submit))
                }
            }

            uiState.error?.let { error ->
                Text(
                    text = when (error) {
                        LoginError.InvalidCredentials ->
                            stringResource(R.string.login_error_credentials)
                        LoginError.NoNetwork ->
                            stringResource(R.string.login_error_network)
                        LoginError.Unexpected -> stringResource(
                            R.string.login_error_unexpected,
                            uiState.errorDetail ?: stringResource(R.string.login_error_no_detail),
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun CredentialProblem.messageRes(): Int = when (this) {
    CredentialProblem.EmailBlank -> R.string.login_email_required
    CredentialProblem.EmailMalformed -> R.string.login_email_malformed
    CredentialProblem.PasswordBlank -> R.string.login_password_required
}

@PreviewLightDark
@Composable
private fun LoginScreenPreview() {
    SnTheme { LoginScreen(uiState = LoginUiState(email = "owner@example.com", password = "secret")) }
}

@PreviewLightDark
@Composable
private fun LoginScreenInvalidCredentialsPreview() {
    SnTheme {
        LoginScreen(
            uiState = LoginUiState(
                email = "owner@example.com",
                password = "wrong",
                error = LoginError.InvalidCredentials,
            ),
        )
    }
}

@PreviewLightDark
@Composable
private fun LoginScreenNoNetworkPreview() {
    SnTheme {
        LoginScreen(
            uiState = LoginUiState(
                email = "owner@example.com",
                password = "secret",
                error = LoginError.NoNetwork,
            ),
        )
    }
}

@PreviewLightDark
@Composable
private fun LoginScreenSubmittingPreview() {
    SnTheme {
        LoginScreen(
            uiState = LoginUiState(
                email = "owner@example.com",
                password = "secret",
                submitting = true,
            ),
        )
    }
}
