package com.snjewellery.admin.domain.draft

import com.snjewellery.admin.domain.RequestFailure
import com.snjewellery.admin.domain.media.StagedImages
import com.snjewellery.admin.domain.net.ConnectivityMonitor
import com.snjewellery.admin.domain.product.CreateProductResult
import com.snjewellery.admin.domain.product.DeleteProductResult
import com.snjewellery.admin.domain.product.LoadProductResult
import com.snjewellery.admin.domain.product.ProductDraft
import com.snjewellery.admin.domain.product.ProductImageRepository
import com.snjewellery.admin.domain.product.ProductRepository
import com.snjewellery.admin.domain.product.ProductStatus
import com.snjewellery.admin.domain.product.StagedUpload
import com.snjewellery.admin.domain.product.RemoveImagesResult
import com.snjewellery.admin.domain.product.StoragePathsResult
import com.snjewellery.admin.domain.product.UpdateProductResult
import com.snjewellery.admin.domain.product.UpdateStatusResult
import com.snjewellery.admin.domain.product.UploadImageResult
import com.snjewellery.admin.domain.product.UploadedImage
import com.snjewellery.admin.domain.product.WriteImagesResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What happens to a piece the owner entered with no signal.
 *
 * The two things worth proving are both about **not doing work twice**: a
 * photograph that already reached Storage must not be sent again — it
 * costs the owner megabytes on the connection that just failed, and
 * leaves an object nobody can name and everybody pays for — and a draft
 * that failed must still be there afterwards, because silently losing it
 * is the failure this whole feature exists to prevent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DraftSyncTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val drafts = FakeDraftRepository()
    private val products = FakeProductRepository()
    private val images = FakeImageRepository()
    private val staged = FakeStagedImages()
    private val connectivity = FakeConnectivity()

    /**
     * `backgroundScope`, because [DraftSync.start] collects connectivity
     * for as long as the app lives and would otherwise leave `runTest`
     * waiting a minute for a coroutine that is never meant to finish.
     */
    private fun sync(scope: CoroutineScope) = DraftSync(
        drafts = drafts,
        uploader = DraftUploader(products, images, staged),
        connectivity = connectivity,
        staged = staged,
        scope = scope,
    )

    @Test
    fun `a waiting draft goes up intact when the connection returns`() = runTest(dispatcher) {
        drafts.save(draft(photos = listOf("a", "b", "c")))
        val sync = sync(backgroundScope)
        sync.start()

        connectivity.state.value = true

        assertEquals(listOf("a", "b", "c"), images.uploads)
        assertEquals(listOf(PRODUCT_ID), products.created)
        // Every photograph, in the owner's order — the first is the main
        // image, which is a promise M7.5 made on screen.
        assertEquals(
            listOf("path/a" to 0, "path/b" to 1, "path/c" to 2),
            images.written.map { it.storagePath to it.displayOrder },
        )
        assertEquals("and it stops waiting", emptyList<String>(), drafts.ids())
    }

    @Test
    fun `photographs already in storage are not sent a second time`() = runTest(dispatcher) {
        drafts.save(
            draft(photos = listOf("a", "b")).copy(uploaded = listOf(upload("a"))),
        )
        val sync = sync(backgroundScope)

        sync.syncNow()

        // Re-sending is several megabytes on the connection that just
        // failed, and leaves an orphan under products/{id}/ that nothing
        // points at and nobody can name.
        assertEquals(listOf("b"), images.uploads)
        assertEquals(
            "the row still describes both, in order",
            listOf("path/a" to 0, "path/b" to 1),
            images.written.map { it.storagePath to it.displayOrder },
        )
    }

    @Test
    fun `each photograph is recorded before the next one starts`() = runTest(dispatcher) {
        // The signal drops again after the second of three.
        drafts.save(draft(photos = listOf("a", "b", "c")))
        images.failFrom = 2
        val sync = sync(backgroundScope)

        sync.syncNow()

        val kept = drafts.byId(PRODUCT_ID)
        assertEquals(
            "a pass cut short must not forget what landed",
            listOf("a", "b"),
            kept?.uploaded?.map { it.localUri },
        )
    }

    @Test
    fun `a draft that failed is still there, with the reason`() = runTest(dispatcher) {
        drafts.save(draft(photos = listOf("a")))
        images.failFrom = 0
        val sync = sync(backgroundScope)

        sync.syncNow()

        val kept = drafts.byId(PRODUCT_ID)
        assertEquals("a draft that vanished on failure is the silent loss", PRODUCT_ID, kept?.productId)
        assertEquals(OFFLINE, kept?.failure)
        assertEquals(emptyList<String>(), products.created)
    }

    @Test
    fun `a sent piece takes its retained photographs with it`() = runTest(dispatcher) {
        drafts.save(draft(photos = listOf("a")))
        val sync = sync(backgroundScope)

        sync.syncNow()

        // files/drafts/ is not reclaimable by the system, so anything
        // left there is left forever.
        assertEquals(listOf("a"), staged.discarded)
    }

    @Test
    fun `being offline stops the pass rather than failing every piece`() = runTest(dispatcher) {
        drafts.save(draft(id = "one", photos = listOf("a")))
        drafts.save(draft(id = "two", photos = listOf("b")))
        images.failFrom = 0
        val sync = sync(backgroundScope)

        sync.syncNow()

        // The connection will not have improved for the second piece, and
        // failing it too would record a failure it never really had.
        assertEquals(emptyList<String>(), images.uploads)
        assertNull(drafts.byId("two")?.failure)
        assertEquals(OFFLINE, drafts.byId("one")?.failure)
    }

    @Test
    fun `a name with no free slug stops being retried`() = runTest(dispatcher) {
        drafts.save(draft(photos = listOf("a")))
        products.slugExhausted = true
        val sync = sync(backgroundScope)

        sync.syncNow()

        assertEquals(PRODUCT_ID, sync.state.value.nameUnavailable)
        assertEquals("and it is still the owner's to fix", listOf(PRODUCT_ID), drafts.ids())
    }

    @Test
    fun `retained files no draft refers to are swept up`() = runTest(dispatcher) {
        drafts.save(draft(photos = listOf("a")))
        staged.retained = mutableSetOf("a", "orphan")
        val sync = sync(backgroundScope)

        sync.syncNow()

        // Left behind when an interrupted save is rolled back and the
        // screen abandoned: the draft row goes, the files do not.
        assertTrue("orphan" !in staged.retained)
    }

    @Test
    fun `a second pass over the same drafts cannot start`() = runTest(dispatcher) {
        drafts.save(draft(photos = listOf("a")))
        val sync = sync(backgroundScope)
        images.gate = CompletableDeferred()

        sync.syncNow()
        sync.syncNow()
        images.gate?.complete(Unit)
        advanceUntilIdle()

        assertEquals("two passes would upload every photograph twice", listOf("a"), images.uploads)
    }

    // ── Fixtures ─────────────────────────────────────────────────────

    private fun draft(id: String = PRODUCT_ID, photos: List<String>) = PendingDraft(
        productId = id,
        draft = ProductDraft(name = "Kundan Choker", categoryId = CATEGORY_ID),
        photoUris = photos,
        savedAt = 0L,
    )

    private fun upload(localUri: String) = StagedUpload(
        localUri = localUri,
        storagePath = "path/$localUri",
        url = "https://example.test/$localUri",
        portrait = false,
    )

    private class FakeConnectivity : ConnectivityMonitor {
        val state = MutableStateFlow(false)
        override val online: Flow<Boolean> get() = state
    }

    private class FakeDraftRepository : DraftRepository {
        private val rows = MutableStateFlow<List<PendingDraft>>(emptyList())

        fun ids() = rows.value.map { it.productId }

        override fun pending(): Flow<List<PendingDraft>> = rows

        override suspend fun byId(productId: String) =
            rows.value.firstOrNull { it.productId == productId }

        override suspend fun save(draft: PendingDraft) {
            rows.value = rows.value.filterNot { it.productId == draft.productId } + draft
        }

        override suspend fun delete(productId: String) {
            rows.value = rows.value.filterNot { it.productId == productId }
        }
    }

    private class FakeStagedImages : StagedImages {
        val discarded = mutableListOf<String>()
        var retained = mutableSetOf<String>()

        override fun newCaptureTarget() = null
        override suspend fun stage(sourceUri: String) = sourceUri
        override suspend fun isPortrait(uri: String) = false
        override suspend fun retain(uri: String) = uri

        override suspend fun discardRetainedExcept(keep: Set<String>) {
            retained = retained.filterTo(mutableSetOf()) { it in keep }
        }

        override suspend fun discard(uri: String) {
            discarded += uri
        }
    }

    private class FakeProductRepository : ProductRepository {
        val created = mutableListOf<String>()
        var slugExhausted = false

        override suspend fun create(id: String, draft: ProductDraft): CreateProductResult {
            if (slugExhausted) return CreateProductResult.SlugExhausted
            created += id
            return CreateProductResult.Created(slug = "kundan-choker")
        }

        override suspend fun delete(id: String) = DeleteProductResult.Deleted
        override suspend fun setStatus(id: String, status: ProductStatus, value: Boolean) =
            UpdateStatusResult.Updated

        override suspend fun byId(id: String) = LoadProductResult.Missing
        override suspend fun update(id: String, draft: ProductDraft) = UpdateProductResult.Updated
    }

    private class FakeImageRepository : ProductImageRepository {
        val uploads = mutableListOf<String>()
        var written = emptyList<UploadedImage>()

        /** Refuse from this photograph of the draft onward. */
        var failFrom: Int? = null

        /**
         * Holds the first upload open, so a second pass is attempted
         * while the first is genuinely mid-flight. Without it the pass
         * finishes before the second call and the guard is never
         * exercised.
         */
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun upload(
            productId: String,
            localUri: String,
            onProgress: (Long, Long) -> Unit,
        ): UploadImageResult {
            gate?.await()

            failFrom?.let { if (uploads.size >= it) return UploadImageResult.Failed(OFFLINE) }

            uploads += localUri
            return UploadImageResult.Uploaded(
                storagePath = "path/$localUri",
                url = "https://example.test/$localUri",
            )
        }

        override suspend fun replaceImages(
            productId: String,
            images: List<UploadedImage>,
        ): WriteImagesResult {
            written = images
            return WriteImagesResult.Written
        }

        override suspend fun remove(storagePaths: List<String>) = RemoveImagesResult.Removed
        override suspend fun storagePathsFor(productId: String) =
            StoragePathsResult.Found(emptyList())
    }

    private companion object {
        const val PRODUCT_ID = "22222222-2222-2222-2222-222222222222"
        const val CATEGORY_ID = "11111111-1111-1111-1111-111111111111"
        val OFFLINE = RequestFailure(offline = true)
    }
}
