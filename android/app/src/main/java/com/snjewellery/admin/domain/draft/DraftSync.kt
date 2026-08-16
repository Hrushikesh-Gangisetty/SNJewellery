package com.snjewellery.admin.domain.draft

import com.snjewellery.admin.di.ApplicationScope
import com.snjewellery.admin.domain.media.StagedImages
import com.snjewellery.admin.domain.net.ConnectivityMonitor
import com.snjewellery.admin.domain.product.StagedUpload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends waiting drafts when there is a connection again.
 *
 * ── Why not WorkManager ──────────────────────────────────────────────
 * WorkManager is the textbook answer and it is a dependency, a worker, a
 * Hilt integration and a scheduling model — for a job whose whole
 * lifetime is "the owner has the app open and the signal came back". This
 * runs in the application's own scope instead, started once the session
 * is admitted, and CLAUDE.md §3.7 asks for exactly that trade: reach for
 * a dependency when the platform genuinely cannot do the job.
 *
 * **The cost, stated plainly: nothing syncs while the app is closed.** A
 * draft written in a basement uploads when the owner next opens the app
 * with a signal, not silently overnight. That is acceptable here because
 * the drafts are on the dashboard — the first thing they see — and
 * because uploading several megabytes of photographs in the background is
 * not obviously the kinder behaviour on a metered connection. If
 * background sync is wanted later, it is WorkManager wrapped around
 * [DraftUploader], not a rewrite.
 *
 * ── Why it is started, not automatic ─────────────────────────────────
 * Every write this makes needs an admin session; RLS refuses otherwise.
 * Starting it from the authenticated part of the app means a signed-out
 * phone does not sit there recording refusals against the owner's
 * drafts. See `RootViewModel`.
 */
@Singleton
class DraftSync @Inject constructor(
    private val drafts: DraftRepository,
    private val uploader: DraftUploader,
    private val connectivity: ConnectivityMonitor,
    private val staged: StagedImages,
    @ApplicationScope private val scope: CoroutineScope,
) {

    /**
     * One pass at a time. Both a reconnection and a **Try now** can arrive
     * while a pass is running, and two passes over the same drafts would
     * upload every photograph twice.
     */
    private val pass = Mutex()

    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private var started = false

    /**
     * Begins watching for a connection. Idempotent — the session state it
     * hangs off re-emits, and starting twice would double every upload.
     */
    fun start() {
        if (started) return
        started = true

        scope.launch {
            connectivity.online
                .filter { it }
                .collect { sync() }
        }
    }

    /** The owner asking directly. Same pass, without waiting for an event. */
    fun syncNow() {
        scope.launch { sync() }
    }

    private suspend fun sync() {
        // tryLock rather than withLock: a pass already running will pick
        // up anything this one would have, so queueing a second is a
        // guaranteed duplicate rather than useful work.
        if (!pass.tryLock()) return

        try {
            val waiting = drafts.pending().first()
            sweepUnreferenced(waiting)

            for (draft in waiting) {
                _state.value = _state.value.copy(sending = draft.productId)

                val result = uploader.upload(draft) { upload ->
                    // Persisted as each photograph lands, so a pass cut
                    // short by the signal dropping again does not forget
                    // which objects exist and send them a second time.
                    val newest = current(draft)
                    drafts.save(newest.copy(uploaded = newest.uploaded.plusOrReplace(upload)))
                }

                when (result) {
                    is DraftUploadResult.Sent -> {
                        drafts.delete(draft.productId)
                        draft.photoUris.forEach { staged.discard(it) }
                    }

                    // Recorded on the draft rather than thrown away: the
                    // dashboard reads it, so the owner is told *why* a
                    // piece is still waiting. It stays in the list either
                    // way — a draft that vanished on failure would be the
                    // silent loss this whole feature exists to prevent.
                    is DraftUploadResult.Failed -> {
                        drafts.save(current(draft).copy(failure = result.failure))
                        // The connection is the likeliest reason and it
                        // will not have improved for the next piece.
                        if (result.failure.offline) break
                    }

                    // Retrying cannot fix a name with no free slug, so the
                    // pass stops offering to. The draft keeps its last
                    // failure and waits for the owner.
                    is DraftUploadResult.NameUnavailable ->
                        _state.value = _state.value.copy(nameUnavailable = draft.productId)
                }
            }
        } finally {
            _state.value = _state.value.copy(sending = null)
            pass.unlock()
        }
    }

    /**
     * Deletes retained photographs no draft refers to any more.
     *
     * `files/drafts/` is not reclaimable by the system — that is the
     * point of it — so anything left there is left forever. It happens
     * when an interrupted save is rolled back and the screen abandoned:
     * the draft row goes, the files do not. Swept here because this is
     * the one place that knows the full set still spoken for.
     */
    private suspend fun sweepUnreferenced(waiting: List<PendingDraft>) =
        staged.discardRetainedExcept(waiting.flatMap { it.photoUris }.toSet())

    /**
     * The draft as it now stands, so a failure is written over the newest
     * copy rather than over the one this pass started with — which would
     * undo the record of the photographs that landed before it failed.
     */
    private suspend fun current(draft: PendingDraft): PendingDraft =
        drafts.byId(draft.productId) ?: draft

    /** One record per photograph, newest wins. */
    private fun List<StagedUpload>.plusOrReplace(upload: StagedUpload): List<StagedUpload> =
        filterNot { it.localUri == upload.localUri } + upload
}

/** What the sync is doing, for the dashboard to show. */
data class SyncState(
    /** The piece being sent right now, by product id. Null when idle. */
    val sending: String? = null,
    /**
     * A piece whose name has no free slug. It will not be retried, and
     * the owner has to change the name — so it is named here rather than
     * left to look like an ordinary failure that might clear itself.
     */
    val nameUnavailable: String? = null,
)
