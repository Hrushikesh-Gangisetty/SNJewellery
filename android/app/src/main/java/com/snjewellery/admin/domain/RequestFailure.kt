package com.snjewellery.admin.domain

/**
 * Why a request failed, in the only terms a screen can act on.
 *
 * [offline] is the distinction that matters: "check your signal" and
 * "something is wrong at our end" send the person holding the phone to
 * completely different places, and getting it wrong is the defect M6.7
 * was built around. [detail] is a code or exception name for the second
 * case, since the owner cannot act on it and will have to quote it to
 * someone who can.
 *
 * Deliberately not an exception. `domain` stays free of Ktor, OkHttp and
 * the Supabase SDK, and a screen has no business pattern-matching on a
 * library's exception hierarchy — least of all this SDK's, which
 * discards the cause (see `TransportFailureRecorder`).
 */
data class RequestFailure(
    val offline: Boolean,
    val detail: String? = null,
)
