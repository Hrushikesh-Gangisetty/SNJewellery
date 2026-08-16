package com.snjewellery.admin.ui.screens.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snjewellery.admin.domain.RequestFailure
import com.snjewellery.admin.domain.catalogue.CatalogueRepository
import com.snjewellery.admin.domain.catalogue.CatalogueResult
import com.snjewellery.admin.domain.catalogue.Category
import com.snjewellery.admin.domain.catalogue.CategoryRepository
import com.snjewellery.admin.domain.catalogue.CreateCategoryResult
import com.snjewellery.admin.domain.catalogue.DeleteCategoryResult
import com.snjewellery.admin.domain.catalogue.RenameCategoryResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The owner's categories, in their own order.
 *
 * ── Three states, as everywhere else ─────────────────────────────────
 * ux.md rule 3. [loading] starts true so a shop with a full list never
 * flashes "no categories yet" while the first request is in flight, and
 * an empty list is a statement with a next step rather than an error.
 */
data class CategoriesUiState(
    val categories: List<Category> = emptyList(),
    val loading: Boolean = true,
    val failure: RequestFailure? = null,
    /** The open dialog. Null when the owner is just looking at the list. */
    val editor: CategoryEditor? = null,
) {
    /** Only true once a load has succeeded — see [loading]. */
    val isEmpty: Boolean get() = categories.isEmpty() && !loading && failure == null
}

/**
 * Adding or changing one category.
 *
 * One type for both, because they are the same dialog with the same field
 * and differ only in what Save does. [category] is what says which:
 * null means the name being typed is not a row yet.
 */
data class CategoryEditor(
    val category: Category?,
    /** What is in the field, echoed as it is typed. */
    val name: String,
    val error: CategoryEditorError? = null,
    /** A request is out. Every control is inert until it lands. */
    val working: Boolean = false,
    /** Delete asked for, not yet confirmed. The one irreversible action. */
    val confirmingDelete: Boolean = false,
)

/**
 * Why the dialog will not close.
 *
 * The first two are answered before any request goes out — not to replace
 * what the database enforces, but because a round trip to be told a name
 * is blank is a round trip on mobile data for nothing.
 */
sealed interface CategoryEditorError {
    data object NameBlank : CategoryEditorError

    /**
     * The owner already has a category with this name. The database does
     * not forbid it — only the slug is unique — but two categories called
     * "Rings" are indistinguishable in the Add Product picker and on the
     * website's shortcuts, which makes them a mistake rather than a
     * choice.
     */
    data object NameTaken : CategoryEditorError

    /** No free slug. Retrying will not help; a different name will. */
    data object SlugExhausted : CategoryEditorError

    /** Deleted from another device. A refresh resolves it, not a retry. */
    data object Missing : CategoryEditorError

    /** Pieces are filed under it, so the database refused the delete. */
    data object InUse : CategoryEditorError

    data class Failed(val failure: RequestFailure) : CategoryEditorError
}

/**
 * Full CRUD on the categories the whole catalogue is filed under.
 *
 * Reads through [CatalogueRepository], the same list the Add Product form
 * and the catalogue filter are built from, so there is one way of asking
 * what categories exist. Writes go through [CategoryRepository].
 *
 * ── Why a written row is put on screen rather than re-read ───────────
 * A create returns the row the database wrote and it is appended;
 * a rename and a delete change the one row they name. Re-reading the
 * whole list after each would be a second request for something already
 * known — and would also reorder the list under the owner's finger if
 * someone else had been editing.
 */
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val catalogueRepository: CatalogueRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(loading = true, failure = null, editor = null) }

        viewModelScope.launch {
            when (val result = catalogueRepository.categories()) {
                is CatalogueResult.Loaded -> _uiState.update {
                    it.copy(categories = result.items, loading = false)
                }

                is CatalogueResult.Failed -> _uiState.update {
                    it.copy(loading = false, failure = result.failure)
                }
            }
        }
    }

    fun onAddRequested() =
        _uiState.update { it.copy(editor = CategoryEditor(category = null, name = "")) }

    fun onEditRequested(category: Category) =
        _uiState.update { it.copy(editor = CategoryEditor(category, category.name)) }

    fun onDismissEditor() = _uiState.update { it.copy(editor = null) }

    /** Clears the error with the keystroke: the owner is already fixing it. */
    fun onNameChange(name: String) = updateEditor { it.copy(name = name, error = null) }

    fun onSave() {
        val editor = _uiState.value.editor ?: return
        if (editor.working) return

        val name = editor.name.trim()
        val error = validate(name, editing = editor.category)
        if (error != null) {
            updateEditor { it.copy(error = error) }
            return
        }

        // Nothing to write, and the database would report a rename that
        // changed a row anyway. Closing is the honest answer.
        if (editor.category != null && editor.category.name == name) {
            onDismissEditor()
            return
        }

        updateEditor { it.copy(working = true, error = null) }

        viewModelScope.launch {
            if (editor.category == null) create(name) else rename(editor.category, name)
        }
    }

    private suspend fun create(name: String) {
        when (val result = categoryRepository.create(name)) {
            is CreateCategoryResult.Created -> _uiState.update {
                // Appended, because create() places the row last in the
                // owner's order — the list stays in the order it is read.
                it.copy(categories = it.categories + result.category, editor = null)
            }

            is CreateCategoryResult.SlugExhausted ->
                failEditor(CategoryEditorError.SlugExhausted)

            is CreateCategoryResult.Failed ->
                failEditor(CategoryEditorError.Failed(result.failure))
        }
    }

    private suspend fun rename(category: Category, name: String) {
        when (val result = categoryRepository.rename(category.id, name)) {
            is RenameCategoryResult.Renamed -> _uiState.update { state ->
                state.copy(
                    categories = state.categories.map {
                        if (it.id == category.id) it.copy(name = name) else it
                    },
                    editor = null,
                )
            }

            is RenameCategoryResult.Missing -> failEditor(CategoryEditorError.Missing)

            is RenameCategoryResult.Failed ->
                failEditor(CategoryEditorError.Failed(result.failure))
        }
    }

    /** Opens the confirmation. Deleting a category cannot be undone. */
    fun onDeleteRequested() = updateEditor { it.copy(confirmingDelete = true, error = null) }

    fun onDeleteCancelled() = updateEditor { it.copy(confirmingDelete = false) }

    fun onDeleteConfirmed() {
        val editor = _uiState.value.editor ?: return
        val category = editor.category ?: return
        if (editor.working) return

        updateEditor { it.copy(working = true, error = null) }

        viewModelScope.launch {
            when (val result = categoryRepository.delete(category.id)) {
                is DeleteCategoryResult.Deleted -> _uiState.update { state ->
                    state.copy(
                        categories = state.categories.filterNot { it.id == category.id },
                        editor = null,
                    )
                }

                is DeleteCategoryResult.InUse -> failEditor(CategoryEditorError.InUse)

                is DeleteCategoryResult.Failed ->
                    failEditor(CategoryEditorError.Failed(result.failure))
            }
        }
    }

    /**
     * The category is gone. Closing the dialog is not enough — the list
     * behind it still shows the row, which is what made the rename fail.
     */
    fun onMissingAcknowledged() = load()

    private fun validate(name: String, editing: Category?): CategoryEditorError? = when {
        name.isBlank() -> CategoryEditorError.NameBlank

        _uiState.value.categories.any {
            it.id != editing?.id && it.name.trim().equals(name, ignoreCase = true)
        } -> CategoryEditorError.NameTaken

        else -> null
    }

    private fun failEditor(error: CategoryEditorError) =
        updateEditor { it.copy(working = false, confirmingDelete = false, error = error) }

    private fun updateEditor(transform: (CategoryEditor) -> CategoryEditor) =
        _uiState.update { state ->
            state.copy(editor = state.editor?.let(transform))
        }
}
