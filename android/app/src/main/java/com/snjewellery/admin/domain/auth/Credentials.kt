package com.snjewellery.admin.domain.auth

/**
 * What is wrong with what was typed, before anything is sent.
 *
 * Local validation exists to save a round trip on a mistake the app can
 * see, not to be strict: the server is the authority on whether an
 * account exists. So this checks shape only — a blank field, or an email
 * with no `@` — and never guesses at valid domains.
 */
enum class CredentialProblem {
    EmailBlank,
    EmailMalformed,
    PasswordBlank,
}

/**
 * Shape checks for the login form.
 *
 * Deliberately not `android.util.Patterns.EMAIL_ADDRESS`: `domain` holds
 * no Android imports, which is what keeps it testable on the JVM. The
 * pattern is also looser than the platform's on purpose — rejecting an
 * address the server would have accepted is a worse failure than sending
 * one request that comes back rejected.
 */
object CredentialRules {

    /** Non-empty local part, `@`, a dot-bearing domain, no whitespace. */
    private val EMAIL = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")

    fun validateEmail(email: String): CredentialProblem? = when {
        email.isBlank() -> CredentialProblem.EmailBlank
        !EMAIL.matches(email.trim()) -> CredentialProblem.EmailMalformed
        else -> null
    }

    /**
     * Only blankness. No length or complexity rule: this form signs in to
     * an existing account, and inventing a minimum here would reject a
     * password the account actually has.
     */
    fun validatePassword(password: String): CredentialProblem? =
        if (password.isBlank()) CredentialProblem.PasswordBlank else null
}
