package com.snjewellery.admin.ui.screens.addproduct

import androidx.lifecycle.SavedStateHandle
import com.snjewellery.admin.domain.RequestFailure
import com.snjewellery.admin.domain.catalogue.CatalogueRepository
import com.snjewellery.admin.domain.catalogue.CatalogueResult
import com.snjewellery.admin.domain.catalogue.Category
import com.snjewellery.admin.domain.catalogue.Purity
import com.snjewellery.admin.domain.media.StagedImages
import com.snjewellery.admin.domain.product.CreateProductResult
import com.snjewellery.admin.domain.product.DeleteProductResult
import com.snjewellery.admin.domain.product.ProductDraft
import com.snjewellery.admin.domain.product.ProductImageRepository
import com.snjewellery.admin.domain.product.ProductRepository
import com.snjewellery.admin.domain.product.RemoveImagesResult
import com.snjewellery.admin.domain.product.UploadImageResult
import com.snjewellery.admin.domain.product.UploadedImage
import com.snjewellery.admin.domain.product.WriteImagesResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * M7.9's guarantee, checked.
 *
 * What this milestone produces is not a screen but an **ordering**: the
 * photographs go into Storage before the `products` row is written, so an
 * interrupted save can leave objects nothing points at but never a piece
 * a customer can reach in a half-made state. An ordering is not something
 * a screenshot shows, and the failure it guards against — the connection
 * dying between the second and third photograph — is awkward to stage by
 * hand and easy to stage here.
 *
 * Local rather than instrumented (unlike `PhotoCompressorTest`, which
 * needs a real `Bitmap`): every collaborator is a domain interface, so the
 * whole pipeline runs on the JVM against fakes that record what they were
 * asked to do and in what order.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddProductSaveTest {

    /**
     * One dispatcher for `Dispatchers.Main` and for `runTest`, so that
     * `advanceUntilIdle` drives the very coroutines `viewModelScope`
     * started. Two would each have their own scheduler and the gated tests
     * would hang.
     *
     * Unconfined, so an ungated `launch` runs to completion without
     * advancing — which is what lets most of these tests read as
     * "call it, then assert".
     */
    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var products: FakeProductRepository
    private lateinit var images: FakeProductImageRepository

    /**
     * The handle outlives the view model on purpose: it is what survives
     * process death, so a second view model built from the same one is
     * what the owner comes back to.
     */
    private lateinit var handle: SavedStateHandle

    @Before
    fun setUp() {
        // `viewModelScope` posts to Dispatchers.Main, which does not exist
        // off-device.
        Dispatchers.setMain(dispatcher)
        products = FakeProductRepository()
        images = FakeProductImageRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    // ── The invariant ────────────────────────────────────────────────

    @Test
    fun `an upload failing part-way writes no product row`() = runTest(dispatcher) {
        images.failFrom = 2
        val viewModel = viewModel(photos = 3)

        viewModel.save()

        assertEquals(
            "the connection died on the third photograph, so nothing should be public",
            emptyList<String>(),
            products.created,
        )
        val state = viewModel.uiState.value.saveState
        assertTrue("$state", state is SaveState.Interrupted)
        state as SaveState.Interrupted
        assertEquals(2, state.uploaded)
        assertEquals(3, state.total)
        assertTrue("nothing reached the catalogue, and it must say so", !state.inCatalogue)
        assertTrue("there are two objects to clean up", state.canDiscard)
    }

    @Test
    fun `discarding an interrupted save removes every uploaded object`() = runTest(dispatcher) {
        images.failFrom = 2
        val viewModel = viewModel(photos = 3)
        viewModel.save()

        viewModel.discard()

        assertEquals(images.uploadedPaths, images.removed)
        assertEquals(
            "no row was written, so there is none to delete",
            emptyList<String>(),
            products.deleted,
        )
        assertEquals(SaveState.Idle, viewModel.uiState.value.saveState)
    }

    @Test
    fun `discarding leaves the form and its photographs alone`() = runTest(dispatcher) {
        images.failFrom = 1
        val viewModel = viewModel(photos = 2)
        viewModel.save()

        viewModel.discard()

        // Rollback undoes what reached the server. Re-typing the piece is
        // the thing the owner must never be made to do.
        assertEquals("Kundan Choker", viewModel.uiState.value.form.name)
        assertEquals(2, viewModel.uiState.value.form.images.size)
    }

    // ── Carrying on ──────────────────────────────────────────────────

    @Test
    fun `carrying on uploads only the photographs that did not land`() = runTest(dispatcher) {
        images.failFrom = 2
        val viewModel = viewModel(photos = 3)
        viewModel.save()
        val afterFirstAttempt = images.uploads.toList()

        images.failFrom = null
        viewModel.save()

        assertEquals(listOf("staged-0", "staged-1"), afterFirstAttempt)
        assertEquals(
            "the two that landed must not be sent a second time",
            listOf("staged-0", "staged-1", "staged-2"),
            images.uploads,
        )
        assertTrue(
            "${viewModel.uiState.value.saveState}",
            viewModel.uiState.value.saveState is SaveState.Saved,
        )
    }

    @Test
    fun `carrying on writes one product, not two`() = runTest(dispatcher) {
        images.failFrom = 1
        val viewModel = viewModel(photos = 2)
        viewModel.save()

        images.failFrom = null
        viewModel.save()

        assertEquals(1, products.created.size)
        assertEquals(
            "the id is fixed for the attempt, so a repeat is the same row",
            products.created.toSet().size,
            products.created.size,
        )
    }

    @Test
    fun `a name rejected after the uploads does not resend them`() = runTest(dispatcher) {
        products.slugExhausted = true
        val viewModel = viewModel(photos = 2)
        viewModel.save()

        assertEquals(SaveState.NameUnavailable, viewModel.uiState.value.saveState)

        // Renaming clears the error. It must not clear two photographs
        // that are already in the bucket.
        products.slugExhausted = false
        viewModel.onNameChange("Kundan Choker Set")
        viewModel.save()

        assertEquals(listOf("staged-0", "staged-1"), images.uploads)
    }

    // ── The one failure that leaves a public piece ────────────────────

    @Test
    fun `image rows failing is reported as the piece being in the catalogue`() = runTest(dispatcher) {
        images.failRows = true
        val viewModel = viewModel(photos = 2)

        viewModel.save()

        val state = viewModel.uiState.value.saveState as SaveState.Interrupted
        assertTrue("the row is written, so 'nothing was saved' would be a lie", state.inCatalogue)
    }

    @Test
    fun `discarding after the row was written deletes the row and the objects`() = runTest(dispatcher) {
        images.failRows = true
        val viewModel = viewModel(photos = 2)
        viewModel.save()

        viewModel.discard()

        assertEquals(images.uploadedPaths, images.removed)
        assertEquals(products.created, products.deleted)
    }

    @Test
    fun `a failed rollback keeps the piece discardable rather than silently giving up`() = runTest(dispatcher) {
        images.failFrom = 2
        val viewModel = viewModel(photos = 3)
        viewModel.save()

        images.failRemove = true
        viewModel.discard()

        val state = viewModel.uiState.value.saveState as SaveState.Interrupted
        assertTrue("the owner must be told the undo did not happen", state.discardFailure != null)

        // And the objects it could not remove are retried, not forgotten.
        images.failRemove = false
        viewModel.discard()
        assertEquals(images.uploadedPaths, images.removed)
    }

    // ── Ordering ─────────────────────────────────────────────────────

    @Test
    fun `display order is the order on screen, not the order uploaded`() = runTest(dispatcher) {
        images.failFrom = 2
        val viewModel = viewModel(photos = 3)
        viewModel.save()

        // Between the two attempts the owner promotes the second
        // photograph. The rows must follow the screen (M7.5), even though
        // it is not the order the objects went up in.
        viewModel.onMoveImageEarlier(1)
        images.failFrom = null
        viewModel.save()

        assertEquals(
            listOf("staged-1", "staged-0", "staged-2"),
            images.written.sortedBy { it.displayOrder }.map { it.storagePath.substringAfter('/') },
        )
        assertEquals(listOf(0, 1, 2), images.written.map { it.displayOrder }.sorted())
    }

    @Test
    fun `a photograph removed after it was uploaded is deleted rather than left paid for`() =
        runTest(dispatcher) {
            images.failFrom = 2
            val viewModel = viewModel(photos = 3)
            viewModel.save()

            viewModel.onRemoveImage(0)
            images.failFrom = null
            viewModel.save()

            assertEquals(listOf("path/staged-0"), images.removed)
            assertEquals(2, images.written.size)
        }

    // ── Interruption (M7.10) ─────────────────────────────────────────

    @Test
    fun `double-tapping Save creates exactly one product`() = runTest(dispatcher) {
        // A gate, because the guard is only meaningful while a request is
        // actually outstanding — with fakes that return immediately the
        // first save finishes before the second tap and the test proves
        // nothing.
        val gate = CompletableDeferred<Unit>()
        images.gate = gate
        val viewModel = viewModel(photos = 2)

        viewModel.save()
        viewModel.save()
        viewModel.save()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, products.created.size)
        assertEquals(
            "each photograph goes up once, not three times",
            listOf("staged-0", "staged-1"),
            images.uploads,
        )
    }

    @Test
    fun `discard cannot be double-tapped either`() = runTest(dispatcher) {
        images.failFrom = 1
        val viewModel = viewModel(photos = 2)
        viewModel.save()

        val gate = CompletableDeferred<Unit>()
        images.gate = gate
        viewModel.discard()
        viewModel.discard()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("path/staged-0"), images.removed)
    }

    @Test
    fun `an attempt the app did not outlive is offered back, not forgotten`() = runTest(dispatcher) {
        images.failFrom = 2
        val first = viewModel(photos = 3)
        first.save()
        // The handle is what survives process death, so a second view
        // model built from the same one is what the owner comes back to.
        val reopened = reopen()

        val state = reopened.uiState.value.saveState as SaveState.Interrupted
        assertEquals(2, state.uploaded)
        assertEquals(3, state.total)
        assertNull("nothing failed — the app closed", state.failure)
        assertTrue("the two objects in the bucket must still be removable", state.canDiscard)
    }

    @Test
    fun `a reopened attempt carries on rather than re-uploading`() = runTest(dispatcher) {
        images.failFrom = 2
        viewModel(photos = 3).save()
        val reopened = reopen()

        images.failFrom = null
        reopened.save()

        assertEquals(listOf("staged-0", "staged-1", "staged-2"), images.uploads)
        assertEquals(1, products.created.size)
        assertTrue("${reopened.uiState.value.saveState}", reopened.uiState.value.saveState is SaveState.Saved)
    }

    @Test
    fun `a reopened attempt keeps the same product id`() = runTest(dispatcher) {
        images.failRows = true
        viewModel(photos = 1).save()
        val idBefore = products.created.single()

        val reopened = reopen()
        images.failRows = false
        reopened.save()

        // A new id would be a second piece in the catalogue for one the
        // owner entered once.
        assertEquals(listOf(idBefore), products.created)
    }

    @Test
    fun `a finished save leaves nothing behind for the next one to resume`() = runTest(dispatcher) {
        viewModel(photos = 1).save()

        val reopened = reopen()

        assertEquals(SaveState.Idle, reopened.uiState.value.saveState)
    }

    // ── Fixtures ─────────────────────────────────────────────────────

    private fun viewModel(photos: Int): AddProductViewModel {
        handle = SavedStateHandle(
            mapOf(
                "name" to "Kundan Choker",
                "category_id" to CATEGORY_ID,
                "images" to ArrayList((0 until photos).map { "staged-$it" }),
            ),
        )
        return reopen()
    }

    /** A view model over whatever the handle currently holds. */
    private fun reopen() = AddProductViewModel(
        catalogueRepository = FakeCatalogueRepository(),
        productRepository = products,
        productImageRepository = images,
        stagedImages = FakeStagedImages(),
        savedState = handle,
    )

    private class FakeCatalogueRepository : CatalogueRepository {
        override suspend fun categories() =
            CatalogueResult.Loaded(listOf(Category(CATEGORY_ID, "Bridal Jewellery", true)))

        override suspend fun purities() =
            CatalogueResult.Loaded(listOf(Purity("p1", "22K", "22K Gold")))
    }

    private class FakeStagedImages : StagedImages {
        override fun newCaptureTarget() = "staged-new"
        override suspend fun stage(sourceUri: String) = sourceUri
        override suspend fun isPortrait(uri: String) = false
        override suspend fun discard(uri: String) = Unit
    }

    private class FakeProductRepository : ProductRepository {
        val created = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        var slugExhausted = false

        override suspend fun create(id: String, draft: ProductDraft): CreateProductResult {
            if (slugExhausted) return CreateProductResult.SlugExhausted
            created += id
            return CreateProductResult.Created(slug = "kundan-choker")
        }

        override suspend fun delete(id: String): DeleteProductResult {
            deleted += id
            return DeleteProductResult.Deleted
        }
    }

    private class FakeProductImageRepository : ProductImageRepository {
        /** Every `upload` call, in order, by staged URI. */
        val uploads = mutableListOf<String>()
        val removed = mutableListOf<String>()
        var written = emptyList<UploadedImage>()

        /** Refuse uploads from this index of the attempt onward. */
        var failFrom: Int? = null
        var failRows = false
        var failRemove = false

        /**
         * Holds the first request open, so a second tap arrives while the
         * first is genuinely in flight. Without it the fakes return before
         * the second tap and the guard is never exercised.
         */
        var gate: CompletableDeferred<Unit>? = null

        /** The paths of everything that actually landed. */
        val uploadedPaths get() = uploads.map { "path/$it" }

        override suspend fun upload(
            productId: String,
            localUri: String,
            onProgress: (Long, Long) -> Unit,
        ): UploadImageResult {
            gate?.await()

            val index = localUri.substringAfterLast('-').toInt()
            failFrom?.let { if (index >= it) return UploadImageResult.Failed(OFFLINE) }

            uploads += localUri
            onProgress(BYTES, BYTES)
            return UploadImageResult.Uploaded(
                storagePath = "path/$localUri",
                url = "https://example.test/path/$localUri",
            )
        }

        override suspend fun replaceImages(
            productId: String,
            images: List<UploadedImage>,
        ): WriteImagesResult {
            if (failRows) return WriteImagesResult.Failed(OFFLINE)
            written = images
            return WriteImagesResult.Written
        }

        override suspend fun remove(storagePaths: List<String>): RemoveImagesResult {
            gate?.await()
            if (failRemove) return RemoveImagesResult.Failed(OFFLINE)
            removed += storagePaths
            return RemoveImagesResult.Removed
        }
    }

    private companion object {
        const val CATEGORY_ID = "11111111-1111-1111-1111-111111111111"
        const val BYTES = 1024L
        val OFFLINE = RequestFailure(offline = true)
    }
}
