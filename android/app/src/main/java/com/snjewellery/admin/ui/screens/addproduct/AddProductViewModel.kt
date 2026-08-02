package com.snjewellery.admin.ui.screens.addproduct

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snjewellery.admin.domain.RequestFailure
import com.snjewellery.admin.domain.catalogue.CatalogueRepository
import com.snjewellery.admin.domain.catalogue.CatalogueResult
import com.snjewellery.admin.domain.catalogue.Category
import com.snjewellery.admin.domain.catalogue.Purity
import com.snjewellery.admin.domain.media.CaptureTargets
import com.snjewellery.admin.domain.product.CreateProductResult
import com.snjewellery.admin.domain.product.FieldProblem
import com.snjewellery.admin.domain.product.ProductDraft
import com.snjewellery.admin.domain.product.ProductFormRules
import com.snjewellery.admin.domain.product.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where the category and purity lists are in their loading. */
sealed interface OptionsState {
    data object Loading : OptionsState
    data class Loaded(val categories: List<Category>, val purities: List<Purity>) : OptionsState
    data class Failed(val failure: RequestFailure) : OptionsState
}

/** What the owner typed. Field names match the PRD's and the schema's. */
data class ProductForm(
    val name: String = "",
    val categoryId: String? = null,
    val purityId: String? = null,
    val weight: String = "",
    val description: String = "",
    /** Raw text; split on commas when saved. */
    val tags: String = "",
    val featured: Boolean = false,
    /**
     * Content URIs of the photographs chosen so far, in the order they
     * will be uploaded. The first is the primary image — M7.5 makes that
     * order editable and says so on screen; M7.7 uploads them.
     */
    val images: List<String> = emptyList(),
)

/**
 * Why a photograph could not be taken, when one could not.
 *
 * Cancelling is **not** here. `TakePicture` reports a cancelled capture
 * and a failed one identically, and cancelling is much the commoner of
 * the two — an error after someone deliberately backed out of the camera
 * would be the app arguing with them.
 */
sealed interface CameraProblem {
    /** Refused this time. Asking again is still allowed. */
    data object PermissionRefused : CameraProblem

    /** Refused for good; only the system settings can undo it. */
    data object PermissionBlocked : CameraProblem

    /** Nothing on the phone answers the capture intent. */
    data object NoCameraApp : CameraProblem

    /** No room to put the photograph. */
    data object NoStorage : CameraProblem
}

/**
 * Problems shown under their fields.
 *
 * Separate from [ProductForm] so what the owner typed and what is wrong
 * with it cannot drift — clearing a problem never risks clearing a value.
 */
data class FormProblems(
    val name: FieldProblem? = null,
    val category: FieldProblem? = null,
    val weight: FieldProblem? = null,
) {
    val any: Boolean get() = name != null || category != null || weight != null
}

/** The outcome of a save, once. */
sealed interface SaveState {
    data object Idle : SaveState
    data object Saving : SaveState
    data class Saved(val slug: String) : SaveState
    data class Failed(val failure: RequestFailure) : SaveState
    data object NameUnavailable : SaveState
}

data class AddProductUiState(
    val form: ProductForm = ProductForm(),
    val problems: FormProblems = FormProblems(),
    val options: OptionsState = OptionsState.Loading,
    val saveState: SaveState = SaveState.Idle,
    val cameraProblem: CameraProblem? = null,
)

/**
 * The Add Product form.
 *
 * ── Why the state is in a SavedStateHandle ───────────────────────────
 * A `ViewModel` alone survives rotation but **not** process death, and
 * process death is the case that matters here: the owner is
 * photographing a ring, switches to the camera, and Android reclaims the
 * app. Losing everything they typed at that moment is the single most
 * annoying thing this screen could do, and it happens on exactly the
 * phones this shop will use. M7.2's requirement is written as "survives
 * configuration change and backgrounding" for that reason.
 *
 * The handle holds the form's fields and, since M7.3, the photographs —
 * plus the capture in flight, because opening the camera is exactly what
 * makes the app a candidate for being reclaimed.
 */
@HiltViewModel
class AddProductViewModel @Inject constructor(
    private val catalogueRepository: CatalogueRepository,
    private val productRepository: ProductRepository,
    private val captureTargets: CaptureTargets,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddProductUiState(form = restoreForm()))
    val uiState: StateFlow<AddProductUiState> = _uiState.asStateFlow()

    init {
        loadOptions()
    }

    fun loadOptions() {
        _uiState.update { it.copy(options = OptionsState.Loading) }

        viewModelScope.launch {
            // Sequential rather than concurrent: two small lookups, and
            // if the first fails the second will too. The dashboard
            // parallelises four because it shows all four at once; here
            // the first failure is the answer.
            val categories = catalogueRepository.categories()
            if (categories is CatalogueResult.Failed) {
                _uiState.update { it.copy(options = OptionsState.Failed(categories.failure)) }
                return@launch
            }

            val purities = catalogueRepository.purities()
            if (purities is CatalogueResult.Failed) {
                _uiState.update { it.copy(options = OptionsState.Failed(purities.failure)) }
                return@launch
            }

            _uiState.update {
                it.copy(
                    options = OptionsState.Loaded(
                        categories = (categories as CatalogueResult.Loaded).items,
                        purities = (purities as CatalogueResult.Loaded).items,
                    ),
                )
            }
        }
    }

    fun onNameChange(value: String) = updateForm({ it.copy(name = value) }) { it.copy(name = null) }
    fun onCategoryChange(id: String) =
        updateForm({ it.copy(categoryId = id) }) { it.copy(category = null) }

    fun onPurityChange(id: String?) = updateForm({ it.copy(purityId = id) })
    fun onWeightChange(value: String) =
        updateForm({ it.copy(weight = value) }) { it.copy(weight = null) }

    fun onDescriptionChange(value: String) = updateForm({ it.copy(description = value) })
    fun onTagsChange(value: String) = updateForm({ it.copy(tags = value) })
    fun onFeaturedChange(value: Boolean) = updateForm({ it.copy(featured = value) })

    /**
     * Validation appears when a field is **left**, not while it is being
     * typed into. Flagging a blank name at the first keystroke tells
     * someone they are wrong before they have finished being right — the
     * same rule the login screen follows.
     */
    fun onNameBlur() = _uiState.update {
        it.copy(problems = it.problems.copy(name = ProductFormRules.validateName(it.form.name)))
    }

    fun onWeightBlur() = _uiState.update {
        it.copy(problems = it.problems.copy(weight = ProductFormRules.validateWeight(it.form.weight)))
    }

    /**
     * Claims somewhere for the camera app to write, and returns the URI
     * to launch it with — or `null`, having said why, when there is
     * nowhere to put a photograph.
     *
     * The pending target goes on the handle rather than into the state
     * flow. Opening the camera puts this app in the background holding a
     * decoded form and a screen's worth of bitmaps, which is precisely
     * when Android reclaims it; the result then arrives on a view model
     * that never made the request, and the handle is what still knows
     * where the photograph went.
     */
    fun newCaptureTarget(): String? {
        val target = captureTargets.newTarget()
        if (target == null) {
            _uiState.update { it.copy(cameraProblem = CameraProblem.NoStorage) }
            return null
        }

        savedState[KEY_PENDING_CAPTURE] = target
        _uiState.update { it.copy(cameraProblem = null) }
        return target
    }

    /**
     * [captured] is the camera app's own report. False covers both a
     * cancelled capture and a failed one — see [CameraProblem] for why
     * neither says anything.
     */
    fun onCaptureFinished(captured: Boolean) {
        val pending: String? = savedState[KEY_PENDING_CAPTURE]
        savedState[KEY_PENDING_CAPTURE] = null

        if (!captured || pending == null) return
        updateForm({ it.copy(images = it.images + pending) })
    }

    /**
     * [canAskAgain] is `shouldShowRequestPermissionRationale` read after
     * the refusal: true while the system will still show the dialog,
     * false once it has stopped. The two need different words because
     * they need different actions — one is "tap again", the other is a
     * trip to Settings, and offering the first when only the second works
     * is a button that does nothing.
     */
    fun onCameraPermissionRefused(canAskAgain: Boolean) = _uiState.update {
        it.copy(
            cameraProblem = if (canAskAgain) {
                CameraProblem.PermissionRefused
            } else {
                CameraProblem.PermissionBlocked
            },
        )
    }

    fun onCameraUnavailable() =
        _uiState.update { it.copy(cameraProblem = CameraProblem.NoCameraApp) }

    fun save() {
        val current = _uiState.value
        // Double-tap guard. M7.10 owns the rest of that task; this much
        // belongs with the only Save button that exists, because without
        // it two taps are two products.
        if (current.saveState is SaveState.Saving) return

        // Everything is checked here, not only on blur: a field never
        // touched has never blurred, and Save must still say what is
        // missing rather than doing nothing.
        val problems = FormProblems(
            name = ProductFormRules.validateName(current.form.name),
            category = ProductFormRules.validateCategory(current.form.categoryId),
            weight = ProductFormRules.validateWeight(current.form.weight),
        )

        if (problems.any) {
            _uiState.update { it.copy(problems = problems) }
            return
        }

        // Safe: validateCategory just proved it non-null. Re-read rather
        // than asserted, because CLAUDE.md forbids `!!` and a null here
        // would be a crash on the owner's only Save button.
        val categoryId = current.form.categoryId ?: return

        _uiState.update { it.copy(problems = problems, saveState = SaveState.Saving) }

        viewModelScope.launch {
            val result = productRepository.create(current.form.toDraft(categoryId))
            _uiState.update {
                it.copy(
                    saveState = when (result) {
                        is CreateProductResult.Created -> SaveState.Saved(result.slug)
                        is CreateProductResult.Failed -> SaveState.Failed(result.failure)
                        is CreateProductResult.SlugExhausted -> SaveState.NameUnavailable
                    },
                )
            }
        }
    }

    /** Lets the screen dismiss an error without re-entering everything. */
    fun onErrorDismissed() = _uiState.update { it.copy(saveState = SaveState.Idle) }

    /**
     * [clearProblem] clears only the problem belonging to the field that
     * changed. A message about a field the owner has not touched is
     * still true, and wiping all of them on every keystroke would hide
     * what Save just told them.
     */
    private fun updateForm(
        transform: (ProductForm) -> ProductForm,
        clearProblem: (FormProblems) -> FormProblems = { it },
    ) {
        _uiState.update { state ->
            val form = transform(state.form)
            persist(form)
            state.copy(
                form = form,
                problems = clearProblem(state.problems),
                // A save error describes the previous attempt and stops
                // being true the moment the form changes.
                saveState = SaveState.Idle,
            )
        }
    }

    private fun persist(form: ProductForm) {
        savedState[KEY_NAME] = form.name
        savedState[KEY_CATEGORY] = form.categoryId
        savedState[KEY_PURITY] = form.purityId
        savedState[KEY_WEIGHT] = form.weight
        savedState[KEY_DESCRIPTION] = form.description
        savedState[KEY_TAGS] = form.tags
        savedState[KEY_FEATURED] = form.featured
        // ArrayList, not List: the handle writes to a Bundle, and a
        // Bundle stores an ArrayList of strings. A plain List goes in as
        // a Serializable and comes back as something else.
        savedState[KEY_IMAGES] = ArrayList(form.images)
    }

    private fun restoreForm() = ProductForm(
        name = savedState[KEY_NAME] ?: "",
        categoryId = savedState[KEY_CATEGORY],
        purityId = savedState[KEY_PURITY],
        weight = savedState[KEY_WEIGHT] ?: "",
        description = savedState[KEY_DESCRIPTION] ?: "",
        tags = savedState[KEY_TAGS] ?: "",
        featured = savedState[KEY_FEATURED] ?: false,
        images = savedState.get<ArrayList<String>>(KEY_IMAGES) ?: emptyList(),
    )

    private companion object {
        const val KEY_NAME = "name"
        const val KEY_CATEGORY = "category_id"
        const val KEY_PURITY = "purity_id"
        const val KEY_WEIGHT = "weight"
        const val KEY_DESCRIPTION = "description"
        const val KEY_TAGS = "tags"
        const val KEY_FEATURED = "featured"
        const val KEY_IMAGES = "images"
        const val KEY_PENDING_CAPTURE = "pending_capture"
    }
}

/**
 * Form text to a draft.
 *
 * Tags are comma-separated because that is how someone types a list on a
 * phone keyboard without a chip editor to fight. Blank entries are
 * dropped and each is trimmed, so `"bridal, temple,"` is two tags.
 */
internal fun ProductForm.toDraft(categoryId: String) = ProductDraft(
    name = name.trim(),
    categoryId = categoryId,
    purityId = purityId,
    weightGrams = weight.trim().toDoubleOrNull(),
    description = description,
    tags = tags.split(',').map(String::trim).filter(String::isNotEmpty),
    featured = featured,
)
