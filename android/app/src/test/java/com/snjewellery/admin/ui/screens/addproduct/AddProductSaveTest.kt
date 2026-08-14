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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

    private lateinit var products: FakeProductRepository
    private lateinit var images: FakeProductImageRepository

    @Before
    fun setUp() {
        // `viewModelScope` posts to Dispatchers.Main, which does not exist
        // off-device. Unconfined so a `launch` runs to its first real
        // suspension immediately and the assertions need no advancing.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        products = FakeProductRepository()
        images = FakeProductImageRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    // ── The invariant ────────────────────────────────────────────────

    @Test
    fun `an upload failing part-way writes no product row`() = runTest {
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
    fun `discarding an interrupted save removes every uploaded object`() = runTest {
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
    fun `discarding leaves the form and its photographs alone`() = runTest {
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
    fun `carrying on uploads only the photographs that did not land`() = runTest {
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
    fun `carrying on writes one product, not two`() = runTest {
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
    fun `a name rejected after the uploads does not resend them`() = runTest {
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
    fun `image rows failing is reported as the piece being in the catalogue`() = runTest {
        images.failRows = true
        val viewModel = viewModel(photos = 2)

        viewModel.save()

        val state = viewModel.uiState.value.saveState as SaveState.Interrupted
        assertTrue("the row is written, so 'nothing was saved' would be a lie", state.inCatalogue)
    }

    @Test
    fun `discarding after the row was written deletes the row and the objects`() = runTest {
        images.failRows = true
        val viewModel = viewModel(photos = 2)
        viewModel.save()

        viewModel.discard()

        assertEquals(images.uploadedPaths, images.removed)
        assertEquals(products.created, products.deleted)
    }

    @Test
    fun `a failed rollback keeps the piece discardable rather than silently giving up`() = runTest {
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
    fun `display order is the order on screen, not the order uploaded`() = runTest {
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
        runTest {
            images.failFrom = 2
            val viewModel = viewModel(photos = 3)
            viewModel.save()

            viewModel.onRemoveImage(0)
            images.failFrom = null
            viewModel.save()

            assertEquals(listOf("path/staged-0"), images.removed)
            assertEquals(2, images.written.size)
        }

    // Two taps landing as two products is M7.10's `Done when`, and testing
    // it needs a fake that actually blocks so the second tap arrives while
    // the first is in flight. Left there rather than half-done here.

    // ── Fixtures ─────────────────────────────────────────────────────

    private fun viewModel(photos: Int) = AddProductViewModel(
        catalogueRepository = FakeCatalogueRepository(),
        productRepository = products,
        productImageRepository = images,
        stagedImages = FakeStagedImages(),
        savedState = SavedStateHandle(
            mapOf(
                "name" to "Kundan Choker",
                "category_id" to CATEGORY_ID,
                "images" to ArrayList((0 until photos).map { "staged-$it" }),
            ),
        ),
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

        /** The paths of everything that actually landed. */
        val uploadedPaths get() = uploads.map { "path/$it" }

        override suspend fun upload(
            productId: String,
            localUri: String,
            onProgress: (Long, Long) -> Unit,
        ): UploadImageResult {
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
