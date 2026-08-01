package com.snjewellery.admin.data.remote

import com.snjewellery.admin.domain.RequestFailure
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a thrown request exception into the reason a screen should state.
 *
 * Extracted in M6.11. M6.9 established this ladder inside the role-gate
 * repository, and the dashboard needed it verbatim — a second copy is
 * how two screens start disagreeing about what "offline" means.
 *
 * The ordering is not arbitrary and is the whole substance of the class:
 *
 * 1. **Timeout** — indistinguishable from being offline from the owner's
 *    point of view, and the advice is the same, so it is not dressed up
 *    as its own failure.
 * 2. **IOException** — every transport failure arrives here, including
 *    TLS ones, because the SDK rethrows them all as
 *    `HttpRequestException`, which extends `IOException` and carries no
 *    cause. The true reason therefore comes from the interceptor that
 *    saw it first. **A TLS failure is not "no connection"**: the socket
 *    opened and the certificate check failed, which on a device means a
 *    wrong clock or an intercepting proxy. Saying "check your signal"
 *    sends someone to look at Wi-Fi for an unrelated problem — the exact
 *    bug M6.7 fixed.
 * 3. **PostgrestRestException** — the server answered and refused. Its
 *    `code` is a Postgres or PostgREST code and is safe to show; the
 *    message is not, as it can quote the request.
 *
 * `CancellationException` is deliberately absent: it is not a failure,
 * and it must keep propagating. Callers catch it separately and rethrow
 * — see the note at each call site.
 */
@Singleton
class RequestFailureClassifier @Inject constructor(
    private val transportFailures: TransportFailureRecorder,
) {

    fun classify(e: Exception): RequestFailure = when (e) {
        is HttpRequestTimeoutException -> RequestFailure(offline = true)

        is IOException -> when (val failure = transportFailures.take()) {
            is TransportFailure.Tls -> RequestFailure(offline = false, detail = failure.detail)
            is TransportFailure.Other -> RequestFailure(offline = false, detail = failure.detail)
            is TransportFailure.Unreachable, null -> RequestFailure(offline = true)
        }

        is PostgrestRestException -> RequestFailure(offline = false, detail = e.code)

        else -> RequestFailure(offline = false, detail = e::class.simpleName)
    }
}
