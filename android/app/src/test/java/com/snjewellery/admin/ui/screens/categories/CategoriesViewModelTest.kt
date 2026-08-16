package com.snjewellery.admin.ui.screens.categories

import com.snjewellery.admin.domain.RequestFailure
import com.snjewellery.admin.domain.catalogue.CatalogueRepository
import com.snjewellery.admin.domain.catalogue.CatalogueResult
import com.snjewellery.admin.domain.catalogue.Category
import com.snjewellery.admin.domain.catalogue.CategoryPosition
import com.snjewellery.admin.domain.catalogue.CategoryRepository
import com.snjewellery.admin.domain.catalogue.CreateCategoryResult
import com.snjewellery.admin.domain.catalogue.DeleteCategoryResult
import com.snjewellery.admin.domain.catalogue.Purity
import com.snjewellery.admin.domain.catalogue.RenameCategoryResult
import com.snjewellery.admin.domain.catalogue.ReorderResult
import com.snjewellery.admin.domain.catalogue.UpdateVisibilityResult
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
 * Category CRUD.
 *
 * The interesting behaviour is all in what happens **before** a request
 * goes out and what happens when one comes back saying no: a blank or
 * duplicate name never reaches the server, a rename that matched no row is
 * not a rename that worked, and a category with pieces in it is refused by
 * the database rather than checked for first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var lists: FakeCatalogueRepository
    private lateinit var categories: FakeCategoryRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        lists = FakeCatalogueRepository()
        categories = FakeCategoryRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the list loads on construction, in the owner's order`() = runTest(dispatcher) {
        val state = viewModel().uiState.value

        assertEquals(listOf("Bridal", "Necklaces"), state.categories.map { it.name })
        assertTrue(!state.loading)
        assertNull(state.failure)
    }

    @Test
    fun `loading starts true, so a full list never flashes the empty state`() =
        runTest(dispatcher) {
            lists.gate = CompletableDeferred()
            val viewModel = viewModel()

            val before = viewModel.uiState.value
            assertTrue(before.loading)
            assertTrue("'no categories yet' must not show mid-request", !before.isEmpty)

            lists.gate?.complete(Unit)
            advanceUntilIdle()
        }

    @Test
    fun `a failed load is an error, and an empty list is not`() = runTest(dispatcher) {
        lists.failure = OFFLINE
        assertEquals(OFFLINE, viewModel().uiState.value.failure)

        lists.failure = null
        lists.categories = emptyList()
        val empty = viewModel().uiState.value
        assertTrue(empty.isEmpty)
        assertNull(empty.failure)
    }

    // ── Adding ───────────────────────────────────────────────────────

    @Test
    fun `a new category is appended rather than re-reading the list`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onAddRequested()
        viewModel.onNameChange("Bangles")

        viewModel.onSave()

        assertEquals(listOf("Bangles"), categories.created)
        assertEquals(
            "create() places it last, so the list stays in the order it is read",
            listOf("Bridal", "Necklaces", "Bangles"),
            viewModel.uiState.value.categories.map { it.name },
        )
        assertNull("and the dialog closes", viewModel.uiState.value.editor)
        assertEquals("one read, not two", 1, lists.reads)
    }

    @Test
    fun `a blank name never reaches the server`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onAddRequested()
        viewModel.onNameChange("   ")

        viewModel.onSave()

        assertEquals(emptyList<String>(), categories.created)
        assertEquals(CategoryEditorError.NameBlank, viewModel.uiState.value.editor?.error)
    }

    @Test
    fun `a duplicate name is refused, whatever its case`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onAddRequested()
        viewModel.onNameChange("  necklaces ")

        viewModel.onSave()

        // The database allows it — only the slug is unique — but two
        // categories called "Necklaces" are indistinguishable everywhere
        // the owner and a customer would meet them.
        assertEquals(emptyList<String>(), categories.created)
        assertEquals(CategoryEditorError.NameTaken, viewModel.uiState.value.editor?.error)
    }

    @Test
    fun `typing clears the error, because the owner is already fixing it`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.onAddRequested()
            viewModel.onSave()
            assertEquals(CategoryEditorError.NameBlank, viewModel.uiState.value.editor?.error)

            viewModel.onNameChange("B")

            assertNull(viewModel.uiState.value.editor?.error)
        }

    @Test
    fun `a name with no free slug asks for a different name, not a retry`() =
        runTest(dispatcher) {
            categories.slugExhausted = true
            val viewModel = viewModel()
            viewModel.onAddRequested()
            viewModel.onNameChange("Bangles")

            viewModel.onSave()

            assertEquals(CategoryEditorError.SlugExhausted, viewModel.uiState.value.editor?.error)
            assertTrue("the dialog stays open with what they typed", viewModel.uiState.value.editor != null)
            assertEquals("Bangles", viewModel.uiState.value.editor?.name)
        }

    // ── Renaming ─────────────────────────────────────────────────────

    @Test
    fun `renaming replaces the row and leaves the others alone`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onEditRequested(viewModel.uiState.value.categories.first())
        viewModel.onNameChange("Bridal Jewellery")

        viewModel.onSave()

        assertEquals(listOf("1" to "Bridal Jewellery"), categories.renamed)
        assertEquals(
            listOf("Bridal Jewellery", "Necklaces"),
            viewModel.uiState.value.categories.map { it.name },
        )
        assertNull(viewModel.uiState.value.editor)
    }

    @Test
    fun `the category's own name is not a duplicate of itself`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val bridal = viewModel.uiState.value.categories.first()
        viewModel.onEditRequested(bridal)

        // Save without changing anything: nothing to write, so nothing is
        // written — and it certainly is not "you already have one of
        // those".
        viewModel.onSave()

        assertEquals(emptyList<Pair<String, String>>(), categories.renamed)
        assertNull("closing is the honest answer", viewModel.uiState.value.editor)
    }

    @Test
    fun `a rename that matched no row is not reported as done`() = runTest(dispatcher) {
        // PostgREST answers 204 for an update matching zero rows exactly
        // as for one that matched, so "no exception" is not "it worked".
        categories.renameMissing = true
        val viewModel = viewModel()
        viewModel.onEditRequested(viewModel.uiState.value.categories.first())
        viewModel.onNameChange("Bridal Jewellery")

        viewModel.onSave()

        assertEquals(CategoryEditorError.Missing, viewModel.uiState.value.editor?.error)
        assertEquals(
            "the old name must not be replaced by one nothing was written for",
            listOf("Bridal", "Necklaces"),
            viewModel.uiState.value.categories.map { it.name },
        )
    }

    @Test
    fun `acknowledging a vanished category re-reads the list`() = runTest(dispatcher) {
        categories.renameMissing = true
        val viewModel = viewModel()
        viewModel.onEditRequested(viewModel.uiState.value.categories.first())
        viewModel.onNameChange("Bridal Jewellery")
        viewModel.onSave()

        lists.categories = listOf(Category("2", "Necklaces", isVisible = true, displayOrder = 9))
        viewModel.onMissingAcknowledged()

        // A refresh, not a retry: the list behind the dialog is what was
        // stale, and retrying has no row to write to.
        assertEquals(listOf("Necklaces"), viewModel.uiState.value.categories.map { it.name })
        assertNull(viewModel.uiState.value.editor)
    }

    // ── Deleting ─────────────────────────────────────────────────────

    @Test
    fun `delete asks first`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onEditRequested(viewModel.uiState.value.categories.first())

        viewModel.onDeleteRequested()

        assertTrue(viewModel.uiState.value.editor?.confirmingDelete == true)
        assertEquals("nothing is deleted by asking", emptyList<String>(), categories.deleted)
    }

    @Test
    fun `a confirmed delete removes the row`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onEditRequested(viewModel.uiState.value.categories.first())
        viewModel.onDeleteRequested()

        viewModel.onDeleteConfirmed()

        assertEquals(listOf("1"), categories.deleted)
        assertEquals(listOf("Necklaces"), viewModel.uiState.value.categories.map { it.name })
        assertNull(viewModel.uiState.value.editor)
    }

    @Test
    fun `a category with pieces in it is kept, and says why`() = runTest(dispatcher) {
        categories.inUse = true
        val viewModel = viewModel()
        viewModel.onEditRequested(viewModel.uiState.value.categories.first())
        viewModel.onDeleteRequested()

        viewModel.onDeleteConfirmed()

        val editor = viewModel.uiState.value.editor
        assertEquals(CategoryEditorError.InUse, editor?.error)
        assertTrue("the confirmation is over — retrying it would fail again", editor?.confirmingDelete == false)
        assertEquals(
            "and nothing left the list",
            listOf("Bridal", "Necklaces"),
            viewModel.uiState.value.categories.map { it.name },
        )
    }

    @Test
    fun `a failed delete keeps the category and the dialog`() = runTest(dispatcher) {
        categories.failure = OFFLINE
        val viewModel = viewModel()
        viewModel.onEditRequested(viewModel.uiState.value.categories.first())
        viewModel.onDeleteRequested()

        viewModel.onDeleteConfirmed()

        assertEquals(
            CategoryEditorError.Failed(OFFLINE),
            viewModel.uiState.value.editor?.error,
        )
        assertEquals(2, viewModel.uiState.value.categories.size)
    }

    // ── Order and visibility (M8.7) ──────────────────────────────────

    @Test
    fun `a move swaps the two rows' positions rather than renumbering`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onMoveLater(viewModel.uiState.value.categories.first())

        // The seed numbers categories 1..11, so the values are gapped.
        // Renumbering by list index would write 0 and 1 here and collide
        // with whatever else already holds them.
        assertEquals(
            listOf(CategoryPosition("1", 9), CategoryPosition("2", 4)),
            categories.positions,
        )
        assertEquals(
            listOf("Necklaces", "Bridal"),
            viewModel.uiState.value.categories.map { it.name },
        )
    }

    @Test
    fun `the ends of the list do not move`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val (first, last) = viewModel.uiState.value.categories.let { it.first() to it.last() }

        viewModel.onMoveEarlier(first)
        viewModel.onMoveLater(last)

        assertEquals(emptyList<CategoryPosition>(), categories.positions)
        assertEquals(
            listOf("Bridal", "Necklaces"),
            viewModel.uiState.value.categories.map { it.name },
        )
    }

    @Test
    fun `a failed move re-reads rather than putting the old order back`() = runTest(dispatcher) {
        val viewModel = viewModel()
        categories.failure = OFFLINE

        viewModel.onMoveLater(viewModel.uiState.value.categories.first())

        // Half the swap may already be written and only the server knows
        // which half, so the list is re-read and the owner is told.
        val state = viewModel.uiState.value
        assertEquals(CategoriesNotice.ReorderFailed(OFFLINE), state.notice)
        assertEquals(listOf("Bridal", "Necklaces"), state.categories.map { it.name })
        assertEquals("one read on construction, one after the failure", 2, lists.reads)
        assertTrue(!state.reordering)
    }

    @Test
    fun `a move naming a category that is gone refreshes the list`() = runTest(dispatcher) {
        val viewModel = viewModel()
        categories.reorderMissing = true
        lists.categories = listOf(Category("2", "Necklaces", isVisible = true, displayOrder = 9))

        viewModel.onMoveLater(viewModel.uiState.value.categories.first())

        val state = viewModel.uiState.value
        assertEquals(CategoriesNotice.ReorderMissing, state.notice)
        assertEquals(listOf("Necklaces"), state.categories.map { it.name })
    }

    @Test
    fun `hiding shows immediately and writes the new value`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onEditRequested(viewModel.uiState.value.categories.first())

        viewModel.onVisibilityChange(false)

        assertEquals(listOf("1" to false), categories.visibilityWrites)
        assertTrue("the row must show it", !viewModel.uiState.value.categories.first().isVisible)
        assertTrue(
            "and so must the open dialog",
            viewModel.uiState.value.editor?.category?.isVisible == false,
        )
    }

    @Test
    fun `a failed hide rolls back to the true value`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onEditRequested(viewModel.uiState.value.categories.first())
        categories.failure = OFFLINE

        viewModel.onVisibilityChange(false)

        val state = viewModel.uiState.value
        assertTrue("a stale optimistic value is worse than none", state.categories.first().isVisible)
        assertTrue(state.editor?.category?.isVisible == true)
        assertEquals(CategoryEditorError.Failed(OFFLINE), state.editor?.error)
    }

    @Test
    fun `hiding a category that is gone is not reported as done`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onEditRequested(viewModel.uiState.value.categories.first())
        categories.visibilityMissing = true

        viewModel.onVisibilityChange(false)

        val state = viewModel.uiState.value
        assertEquals(CategoryEditorError.Missing, state.editor?.error)
        assertTrue("the optimistic value must not stick", state.categories.first().isVisible)
    }

    // ── Fixtures ─────────────────────────────────────────────────────

    private fun viewModel() = CategoriesViewModel(lists, categories)

    private class FakeCatalogueRepository : CatalogueRepository {
        // 1-based and gapped, exactly as the seed numbers them — an
        // index-based reorder would pass against 0, 1, 2 and be wrong
        // against real data.
        var categories = listOf(
            Category("1", "Bridal", isVisible = true, displayOrder = 4),
            Category("2", "Necklaces", isVisible = true, displayOrder = 9),
        )
        var failure: RequestFailure? = null

        /** How many times the list was read — a create must not add one. */
        var reads = 0

        /** Holds the read open, so the first frame can be observed. */
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun categories(): CatalogueResult<Category> {
            gate?.await()
            reads++
            return failure?.let { CatalogueResult.Failed(it) }
                ?: CatalogueResult.Loaded(categories)
        }

        override suspend fun purities() = CatalogueResult.Loaded(listOf(Purity("p", "22K", "22K")))
    }

    private class FakeCategoryRepository : CategoryRepository {
        val created = mutableListOf<String>()
        val renamed = mutableListOf<Pair<String, String>>()
        val deleted = mutableListOf<String>()

        /** Every visibility write, in order: (id, value). */
        val visibilityWrites = mutableListOf<Pair<String, Boolean>>()

        /** Every position written, in the order the rows were sent. */
        val positions = mutableListOf<CategoryPosition>()

        var slugExhausted = false
        var renameMissing = false
        var inUse = false
        var visibilityMissing = false
        var reorderMissing = false
        var failure: RequestFailure? = null

        override suspend fun create(name: String): CreateCategoryResult {
            failure?.let { return CreateCategoryResult.Failed(it) }
            if (slugExhausted) return CreateCategoryResult.SlugExhausted
            created += name
            return CreateCategoryResult.Created(
                Category(
                    id = "new-${created.size}",
                    name = name,
                    isVisible = true,
                    displayOrder = NEXT_POSITION,
                ),
            )
        }

        override suspend fun rename(id: String, name: String): RenameCategoryResult {
            failure?.let { return RenameCategoryResult.Failed(it) }
            if (renameMissing) return RenameCategoryResult.Missing
            renamed += id to name
            return RenameCategoryResult.Renamed
        }

        override suspend fun setVisible(id: String, visible: Boolean): UpdateVisibilityResult {
            failure?.let { return UpdateVisibilityResult.Failed(it) }
            if (visibilityMissing) return UpdateVisibilityResult.Missing
            visibilityWrites += id to visible
            return UpdateVisibilityResult.Updated
        }

        override suspend fun reorder(positions: List<CategoryPosition>): ReorderResult {
            failure?.let { return ReorderResult.Failed(it) }
            if (reorderMissing) return ReorderResult.Missing
            this.positions += positions
            return ReorderResult.Reordered
        }

        override suspend fun delete(id: String): DeleteCategoryResult {
            failure?.let { return DeleteCategoryResult.Failed(it) }
            if (inUse) return DeleteCategoryResult.InUse
            deleted += id
            return DeleteCategoryResult.Deleted
        }
    }

    private companion object {
        const val NEXT_POSITION = 12
        val OFFLINE = RequestFailure(offline = true)
    }
}
