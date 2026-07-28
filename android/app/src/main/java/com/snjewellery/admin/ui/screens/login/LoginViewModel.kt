package com.snjewellery.admin.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snjewellery.admin.domain.auth.AuthRepository
import com.snjewellery.admin.domain.auth.CredentialProblem
import com.snjewellery.admin.domain.auth.CredentialRules
import com.snjewellery.admin.domain.auth.SignInResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Why sign-in failed, for the screen to word. */
enum class LoginError {
    InvalidCredentials,
    NoNetwork,
    Unexpected,
}

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val submitting: Boolean = false,
    /** Shown under a field, only after that field has been touched. */
    val emailProblem: CredentialProblem? = null,
    val passwordProblem: CredentialProblem? = null,
    val error: LoginError? = null,
    /** Set with [LoginError.Unexpected], where the cause is worth quoting. */
    val errorDetail: String? = null,
    val signedIn: Boolean = false,
) {
    /**
     * Both fields non-blank. Deliberately not "and valid": disabling the
     * button on a malformed email hides *why* nothing happens when it is
     * pressed. Pressing it surfaces the reason instead.
     */
    val canSubmit: Boolean
        get() = !submitting && email.isNotBlank() && password.isNotBlank()
}

/**
 * The login screen's view model.
 *
 * Validation is deliberately quiet while typing. Problems appear when the
 * field is left or when submit is pressed — an email flagged malformed at
 * the second character is telling someone they are wrong before they have
 * finished being right.
 *
 * The password never leaves this state object and is never logged.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) = _uiState.update {
        // Clearing the problem and the error on edit: a message about the
        // previous attempt is wrong the moment the input changes.
        it.copy(email = value, emailProblem = null, error = null, errorDetail = null)
    }

    fun onPasswordChange(value: String) = _uiState.update {
        it.copy(password = value, passwordProblem = null, error = null, errorDetail = null)
    }

    fun onPasswordVisibilityToggle() = _uiState.update {
        it.copy(passwordVisible = !it.passwordVisible)
    }

    /** Called when a field loses focus, so problems appear on leaving it. */
    fun onEmailBlur() = _uiState.update {
        if (it.email.isBlank()) it else it.copy(emailProblem = CredentialRules.validateEmail(it.email))
    }

    fun signIn() {
        val current = _uiState.value
        if (current.submitting) return

        val emailProblem = CredentialRules.validateEmail(current.email)
        val passwordProblem = CredentialRules.validatePassword(current.password)

        if (emailProblem != null || passwordProblem != null) {
            _uiState.update {
                it.copy(emailProblem = emailProblem, passwordProblem = passwordProblem)
            }
            return
        }

        _uiState.update { it.copy(submitting = true, error = null, errorDetail = null) }

        viewModelScope.launch {
            val result = authRepository.signIn(current.email, current.password)
            _uiState.update {
                when (result) {
                    is SignInResult.Success ->
                        // The password is dropped from state on success —
                        // it has no further use, and holding it would keep
                        // it in memory for the life of the process.
                        it.copy(submitting = false, password = "", signedIn = true)

                    is SignInResult.InvalidCredentials ->
                        it.copy(submitting = false, error = LoginError.InvalidCredentials)

                    is SignInResult.NoNetwork ->
                        it.copy(submitting = false, error = LoginError.NoNetwork)

                    is SignInResult.Failed ->
                        it.copy(
                            submitting = false,
                            error = LoginError.Unexpected,
                            errorDetail = result.detail,
                        )
                }
            }
        }
    }
}
