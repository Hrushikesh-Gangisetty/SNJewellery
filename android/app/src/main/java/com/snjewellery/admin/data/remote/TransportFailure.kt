package com.snjewellery.admin.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

/**
 * Why the last HTTP attempt failed to reach the server.
 *
 * [Unreachable] is a genuine connectivity problem — DNS, route, connect,
 * timeout. [Tls] is not: the socket opened and the certificate check then
 * failed, which on a device means a wrong clock or an intercepting proxy
 * far more often than anything about the network.
 */
sealed interface TransportFailure {
    data object Unreachable : TransportFailure
    data class Tls(val detail: String?) : TransportFailure
    data class Other(val detail: String?) : TransportFailure
}

/**
 * Records the true cause of a transport failure, because the Supabase SDK
 * destroys it.
 *
 * **Why this exists.** supabase-kt catches any transport exception, logs
 * it, and rethrows `HttpRequestException` — which extends `IOException`
 * and takes no `cause`. The original type and its chain are gone by the
 * time a repository sees it, so every failure looks identical to being
 * offline. Observed, not assumed: a stale emulator clock produced
 * `CertificateNotYetValidException`, and the app confidently reported "no
 * connection" while DNS and the socket were both fine.
 *
 * An OkHttp interceptor sits *below* the SDK and sees the real exception,
 * so it classifies the failure on the way out. A repository then consults
 * this instead of inspecting an exception that no longer carries the
 * answer.
 *
 * Matching on `HttpRequestException`'s message text was the alternative,
 * and it would break the first time either library rewords a string.
 *
 * ── The honest limitation ────────────────────────────────────────────
 * This holds one value for the whole app, so a repository reads *the last*
 * failure rather than *its* failure. With one screen signing in at a time
 * that is exact. It is not a general mechanism, and it must not become
 * one: if two callers ever race, the loser reads the other's cause. Guard
 * that by scoping this per call — a Ktor attribute, or an OkHttp tag —
 * before anything concurrent depends on it.
 */
@Singleton
class TransportFailureRecorder @Inject constructor() {

    @Volatile
    private var last: TransportFailure? = null

    /** Consumes the recorded failure, so a later success cannot read a stale one. */
    fun take(): TransportFailure? = last.also { last = null }

    internal fun record(failure: TransportFailure) {
        last = failure
    }
}

/**
 * Classifies transport exceptions on their way out of OkHttp, then
 * rethrows unchanged. It never swallows: the caller still fails exactly
 * as it would have.
 */
class TransportFailureInterceptor @Inject constructor(
    private val recorder: TransportFailureRecorder,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response = try {
        chain.proceed(chain.request())
    } catch (e: SSLException) {
        // Checked before IOException: SSLException extends it, and the
        // distinction is the entire point of this class.
        recorder.record(TransportFailure.Tls(e::class.simpleName))
        throw e
    } catch (e: UnknownHostException) {
        recorder.record(TransportFailure.Unreachable)
        throw e
    } catch (e: IOException) {
        recorder.record(TransportFailure.Unreachable)
        throw e
    } catch (e: RuntimeException) {
        // A denied socket (missing INTERNET) arrives as SecurityException,
        // which is not an IOException and would otherwise be invisible.
        recorder.record(TransportFailure.Other(e::class.simpleName))
        throw e
    }
}
