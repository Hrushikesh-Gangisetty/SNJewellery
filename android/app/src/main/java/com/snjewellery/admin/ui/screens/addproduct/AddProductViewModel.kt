package com.snjewellery.admin.ui.screens.addproduct

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snjewellery.admin.domain.RequestFailure
import com.snjewellery.admin.domain.catalogue.CatalogueRepository
import com.snjewellery.admin.domain.draft.DraftRepository
import com.snjewellery.admin.domain.draft.PendingDraft
import com.snjewellery.admin.domain.catalogue.CatalogueResult
import com.snjewellery.admin.domain.catalogue.Category
import com.snjewellery.admin.domain.catalogue.Purity
import com.snjewellery.admin.domain.media.StagedImages
import com.snjewellery.admin.domain.product.CreateProductResult
import com.snjewellery.admin.domain.product.DeleteProductResult
import com.snjewellery.admin.domain.product.FieldProblem
import com.snjewellery.admin.domain.product.LoadProductResult
import com.snjewellery.admin.domain.product.ProductDraft
import com.snjewellery.admin.domain.product.ProductFormRules
import com.snjewellery.admin.domain.product.ProductImageRepository
import com.snjewellery.admin.domain.product.ProductRepository
import com.snjewellery.admin.domain.product.StagedUpload
import com.snjewellery.admin.domain.product.RemoveImagesResult
import com.snjewellery.admin.domain.product.StoredPhoto
import com.snjewellery.admin.domain.product.UploadImageResult
import com.snjewellery.admin.domain.product.UpdateProductResult
import com.snjewellery.admin.domain.product.UploadedImage
import com.snjewellery.admin.domain.product.WriteImagesResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

/**
 * Whether this form is entering a new piece or changing an existing one.
 *
 * One screen serves both — CLAUDE.md §11's reuse rule, and the PRD asks
 * for the same eight fields either way. What differs is only the wording,
 * and what Save does.
 */
sealed interface FormMode {
    data object Adding : FormMode

    /**
     * [slug] is carried for the confirmation's link and is **not**
     * re-derived from an edited name — see `ProductRepository.update`.
     *
     * The photographs are **not** here: since M8.3b they live in
     * [ProductForm.images] like any others, because the owner edits them
     * the same way whichever mode the form is in.
     */
    data class Editing(
        val productId: String,
        val slug: String,
    ) : FormMode
}

/** How the existing piece's load went. Only ever anything but Loaded in edit mode. */
sealed interface LoadState {
    data object Ready : LoadState
    data object Loading : LoadState

    /** The piece is gone — deleted elsewhere while the list was stale. */
    data object Missing : LoadState

    data class Failed(val failure: RequestFailure) : LoadState
}

/** Where the category and purity lists are in their loading. */
sealed interface OptionsState {
    data object Loading : OptionsState
    data class Loaded(val categories: List<Category>, val purities: List<Purity>) : OptionsState
    data class Failed(val failure: RequestFailure) : OptionsState
}

/**
 * One photograph on the form, wherever it came from.
 *
 * Adding a piece produces only [Staged] entries. **Editing produces a
 * mixture**, and that mixture is the whole of M8.3b: the two behave
 * identically to the owner — same thumbnail, same arrows, same remove
 * button — and completely differently at save time. A [Staged] one has to
 * be uploaded; a [Stored] one is already in the bucket and must not be
 * sent again, but must be *deleted* from it if the owner removes it.
 *
 * Both render from [displayModel] without the screen asking which it is:
 * Coil takes a local URI and a remote URL alike.
 */
@Serializable
sealed interface FormPhoto {
    /** What Coil loads. A file on the device, or a public URL. */
    val displayModel: String

    /** Stable across a reorder, so `LazyColumn` keys and progress track the right one. */
    val key: String

    /** Compressed and waiting on the device. Not yet in Storage. */
    @Serializable
    data class Staged(val localUri: String) : FormPhoto {
        override val displayModel: String get() = localUri
        override val key: String get() = localUri
    }

    /**
     * Already in the bucket, on a piece being edited.
     *
     * [storagePath] is what a removal has to delete and what the row
     * points at; [url] is what the screen shows. ADR-0005 keeps both for
     * exactly this reason.
     */
    @Serializable
    data class Stored(
        val storagePath: String,
        val url: String,
        /**
         * The frame it is already published in. Carried so that saving an
         * edit rewrites the row it came from rather than flattening every
         * portrait photograph to the square default.
         */
        val portrait: Boolean = false,
    ) : FormPhoto {
        override val displayModel: String get() = url
        override val key: String get() = storagePath
    }
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
     * The photographs, in the order they will be persisted. The first is
     * the primary image — M7.5 makes that order editable and says so on
     * screen.
     *
     * A mixture when editing: see [FormPhoto].
     */
    val images: List<FormPhoto> = emptyList(),
)

/**
 * Why a photograph could not be added, when one could not.
 *
 * Cancelling is **not** here, from either route. `TakePicture` reports a
 * cancelled capture and a failed one identically, and the picker returns
 * an empty selection when it is dismissed — in both cases backing out is
 * much the commoner reading, and an error after someone deliberately
 * changed their mind would be the app arguing with them.
 */
sealed interface PhotoProblem {
    /** The camera was refused this time. Asking again is still allowed. */
    data object CameraRefused : PhotoProblem

    /** The camera was refused for good; only the system settings can undo it. */
    data object CameraBlocked : PhotoProblem

    /** Nothing on the phone answers the capture intent. */
    data object NoCameraApp : PhotoProblem

    /** Nothing on the phone answers the photo picker. */
    data object NoGalleryApp : PhotoProblem

    /** No room to put the photograph. */
    data object NoStorage : PhotoProblem

    /**
     * Some of a selection could not be copied in, and [count] says how
     * many — because the ones that succeeded are on screen, and a bare
     * "something failed" leaves the owner counting thumbnails to work
     * out what they still have to do.
     */
    data class SomeNotAdded(val count: Int) : PhotoProblem
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

/** Where a save has got to. */
sealed interface SaveState {
    data object Idle : SaveState

    /**
     * The photographs are going up. First, because until every one has
     * landed there is nothing in the catalogue to be half-finished —
     * see [AddProductViewModel].
     */
    data class Uploading(val completed: Int, val total: Int) : SaveState

    /** The photographs are up and the rows are being written. */
    data object Saving : SaveState

    /** The rollback is running. */
    data object Discarding : SaveState

    /**
     * [name] is carried alongside the slug so the confirmation can name
     * the piece rather than showing a URL fragment. They are read together
     * because the form is cleared the moment the screen leaves, and a
     * confirmation that says "Saved" without saying *what* is the kind of
     * message someone stops reading.
     */
    data class Saved(val name: String, val slug: String) : SaveState

    /**
     * The name cannot be made into a free slug. Its own state because
     * nothing about it is transient — retrying unchanged gets the same
     * answer, and the owner has to change the name. The photographs
     * already uploaded are kept, so changing it and saving again does not
     * send them a second time.
     */
    data object NameUnavailable : SaveState

    /**
     * The save stopped part-way and can be finished or undone.
     *
     * One state for every recoverable failure, rather than a separate one
     * per step, because the owner's two choices are the same whichever
     * step it was: carry on, or discard it. What differs is only the
     * wording, and that is what the fields are for.
     */
    data class Interrupted(
        /** Photographs already in Storage. Zero when the first one failed. */
        val uploaded: Int,
        val total: Int,
        /**
         * True once the `products` row exists — so the piece is on the
         * website, and "nothing was saved" would be a lie. False in the
         * ordinary case, because the row is written last.
         */
        val inCatalogue: Boolean,
        /**
         * Why it stopped — or **null when nothing failed**: the app was
         * reclaimed while the upload was running, so there is no request
         * to report on. It needs different words rather than a borrowed
         * "no connection", which would send the owner to look at their
         * signal for a problem that was never theirs.
         */
        val failure: RequestFailure?,
        /** Set when a rollback was asked for and could not finish. */
        val discardFailure: RequestFailure? = null,
    ) : SaveState {
        /** Nothing to undo means no reason to offer undoing it. */
        val canDiscard: Boolean get() = uploaded > 0 || inCatalogue
    }
}

/** Whether a request belonging to a save is out on the wire right now. */
internal val SaveState.inFlight: Boolean
    get() = this is SaveState.Uploading ||
        this is SaveState.Saving ||
        this is SaveState.Discarding

/**
 * What a save attempt has already achieved, so the next one does not do
 * it again.
 *
 * Held outside [AddProductUiState] because it is not something the screen
 * draws, and because it must **outlive** the state it produced: fixing a
 * rejected name clears the error but must not throw away four photographs
 * that are already in the bucket.
 *
 * Since M7.10 it must also outlive the **process**. The app is reclaimed
 * mid-upload exactly when it is most loaded — several megabytes of bitmap
 * and a request in flight — and without this the objects already in the
 * bucket become orphans nothing remembers, on a screen that comes back
 * looking as though nothing had happened. So it is written to the
 * `SavedStateHandle` as JSON, which is why every field here is a
 * primitive or a list of them.
 */
@Serializable
internal data class SaveProgress(
    /**
     * Chosen here rather than by the database, because the photographs go
     * up under `products/{id}/…` before the row exists. Fixed for the
     * whole attempt, so a save that somehow ran twice would write the same
     * row twice rather than two rows.
     */
    val productId: String,
    /**
     * A list, not a map keyed by URI: the lookups are by `localUri` all
     * the same, but there are three or four of them and a list survives a
     * round trip through JSON without a key-type question.
     */
    val uploaded: List<StagedUpload> = emptyList(),
    /** Non-null once the `products` row exists. */
    val slug: String? = null,
    /**
     * Objects that should be gone from the bucket but are not, because the
     * delete that would have removed them failed. Retried on the next
     * save or discard — an object nothing points at costs money for as
     * long as it is there.
     */
    val abandoned: List<String> = emptyList(),
) {
    fun landed(localUri: String): StagedUpload? = uploaded.firstOrNull { it.localUri == localUri }

    fun orphanedPaths(): List<String> = abandoned + uploaded.map { it.storagePath }

    /** Whether anything reached the server that would have to be undone. */
    val outstanding: Boolean get() = uploaded.isNotEmpty() || slug != null || abandoned.isNotEmpty()
}

data class AddProductUiState(
    val form: ProductForm = ProductForm(),
    val problems: FormProblems = FormProblems(),
    val options: OptionsState = OptionsState.Loading,
    val saveState: SaveState = SaveState.Idle,
    val photoProblem: PhotoProblem? = null,
    /** A gallery selection is being copied in. Can be several megabytes. */
    val addingPhotos: Boolean = false,
    /**
     * How far each photograph has got, `0f`–`1f`, keyed by its staged
     * URI. Present only while uploading, and only for photographs that
     * have started — M7.7 requires progress visible for **every** image,
     * which a single overall bar cannot give.
     */
    val uploadProgress: Map<String, Float> = emptyMap(),
    /** Adding, or changing an existing piece. */
    val mode: FormMode = FormMode.Adding,
    /** How reading that existing piece went. Always Ready when adding. */
    val loadState: LoadState = LoadState.Ready,
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
    private val productImageRepository: ProductImageRepository,
    private val stagedImages: StagedImages,
    private val draftRepository: DraftRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddProductUiState(form = restoreForm()))
    val uiState: StateFlow<AddProductUiState> = _uiState.asStateFlow()

    /**
     * The piece being edited, from the navigation route — null when this
     * is a new one.
     *
     * Read from the handle rather than passed in: type-safe routes put
     * their arguments there, and taking it as a constructor parameter
     * would mean two ways of building this view model.
     */
    private val editingId: String? = savedState[KEY_PRODUCT_ID]

    /**
     * Objects the owner took off a piece being edited.
     *
     * Not deleted when the button is tapped: nothing on this form is
     * committed until Save, and destroying a photograph that the owner
     * then backs out of removing would be unrecoverable. Handed to
     * `clearAbandoned` at save time, which already knows how to retry a
     * delete that fails.
     */
    private val removedStored = mutableListOf<String>()

    private var progressBacking: SaveProgress? = restoreProgress()

    /**
     * What the current save attempt has already got done. Null when there
     * is no attempt outstanding.
     *
     * Written through to the [SavedStateHandle] on every change rather than
     * at some checkpoint, because there is no moment at which the app is
     * told it is about to be reclaimed. The one thing that must never
     * happen is an object in the bucket that this record does not mention.
     */
    private var progress: SaveProgress?
        get() = progressBacking
        set(value) {
            progressBacking = value
            savedState[KEY_PROGRESS] = value?.let { Json.encodeToString(it) }
        }

    init {
        if (editingId != null) loadExisting(editingId)

        // An attempt that outlived its own coroutine. The upload stopped
        // when the process did, so the screen must say so and offer the
        // same two ways out as any other interruption — coming back to a
        // blank Save button would leave paid-for objects in the bucket
        // that nothing on screen refers to.
        progressBacking?.takeIf { it.outstanding }?.let { attempt ->
            _uiState.update {
                it.copy(
                    saveState = SaveState.Interrupted(
                        uploaded = attempt.uploaded.size,
                        total = it.form.images.size,
                        inCatalogue = attempt.slug != null,
                        // Nothing failed. The app was closed.
                        failure = null,
                    ),
                    uploadProgress = attempt.uploaded.associate { u -> u.localUri to 1f },
                )
            }
        }

        loadOptions()
    }

    /**
     * Fills the form from the piece being edited.
     *
     * Only when the form is otherwise untouched. A `SavedStateHandle` that
     * already holds a name is a form the owner was in the middle of — the
     * app was reclaimed and has just come back — and re-reading the server
     * over it would throw away their edits at exactly the moment M7.2's
     * whole design exists to preserve them.
     */
    fun loadExisting(productId: String) {
        _uiState.update { it.copy(loadState = LoadState.Loading) }

        viewModelScope.launch {
            when (val result = productRepository.byId(productId)) {
                is LoadProductResult.Loaded -> {
                    val product = result.product
                    val mode = FormMode.Editing(
                        productId = product.id,
                        slug = product.slug,
                    )
                    val restored = savedState.get<String>(KEY_NAME) != null

                    _uiState.update {
                        it.copy(
                            mode = mode,
                            loadState = LoadState.Ready,
                            form = if (restored) {
                                it.form
                            } else {
                                product.draft.toForm().copy(images = product.photos.map(::asFormPhoto))
                            },
                        )
                    }
                    if (!restored) persist(_uiState.value.form)
                }

                is LoadProductResult.Missing ->
                    _uiState.update { it.copy(loadState = LoadState.Missing) }

                is LoadProductResult.Failed ->
                    _uiState.update { it.copy(loadState = LoadState.Failed(result.failure)) }
            }
        }
    }

    /** Re-reads the piece after a failure. No-op when adding. */
    fun retryLoad() {
        editingId?.let { loadExisting(it) }
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
        val target = stagedImages.newCaptureTarget()
        if (target == null) {
            _uiState.update { it.copy(photoProblem = PhotoProblem.NoStorage) }
            return null
        }

        savedState[KEY_PENDING_CAPTURE] = target
        _uiState.update { it.copy(photoProblem = null) }
        return target
    }

    /**
     * [captured] is the camera app's own report. False covers both a
     * cancelled capture and a failed one — see [PhotoProblem] for why
     * neither says anything.
     *
     * What the camera wrote is a raw full-resolution photograph, so it is
     * staged like any other source and then thrown away. It is the larger
     * of the two by an order of magnitude, and keeping it would fill the
     * cache over a morning's shooting for no benefit — ADR-0005 does not
     * archive originals.
     */
    fun onCaptureFinished(captured: Boolean) {
        val pending: String? = savedState[KEY_PENDING_CAPTURE]
        savedState[KEY_PENDING_CAPTURE] = null

        if (!captured || pending == null) return
        addPhotos(listOf(pending), discardSources = true)
    }

    /**
     * Stages a gallery selection, in the order the picker returned it.
     */
    fun onGallerySelection(sourceUris: List<String>) {
        // Dismissing the picker returns nothing. That is not a failure
        // and must not be reported as one.
        if (sourceUris.isEmpty()) return
        addPhotos(sourceUris, discardSources = false)
    }

    /**
     * The one path both routes take: compress, add, and say what failed.
     *
     * ── Why this is the only place the form shows progress ───────────
     * Staging is real work — several megapixels decoded, rotated,
     * resized and re-encoded, per photograph — and a control that appears
     * to do nothing for a second gets pressed again.
     *
     * Each photograph is added as it lands rather than all at once at the
     * end, so a selection that partly fails still gives the owner what
     * worked, and a long selection fills in as it goes.
     *
     * [discardSources] is true only for a camera capture, which is the
     * one source this app owns and is therefore entitled to delete.
     */
    private fun addPhotos(sourceUris: List<String>, discardSources: Boolean) {
        _uiState.update { it.copy(photoProblem = null, addingPhotos = true) }

        viewModelScope.launch {
            var failed = 0

            sourceUris.forEach { source ->
                val staged = stagedImages.stage(source)
                if (staged == null) {
                    failed++
                } else {
                    updateForm({ it.copy(images = it.images + FormPhoto.Staged(staged)) })
                }
                if (discardSources) stagedImages.discard(source)
            }

            _uiState.update {
                it.copy(
                    addingPhotos = false,
                    photoProblem = if (failed > 0) PhotoProblem.SomeNotAdded(failed) else null,
                )
            }
        }
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
            photoProblem = if (canAskAgain) {
                PhotoProblem.CameraRefused
            } else {
                PhotoProblem.CameraBlocked
            },
        )
    }

    /**
     * Moves a photograph one place towards the front, where the front is
     * the primary image.
     *
     * The list **is** the order. There is no separate `display_order` to
     * keep in step, so what M7.8 writes is what the owner is looking at,
     * and the two cannot disagree.
     */
    fun onMoveImageEarlier(index: Int) = moveImage(index, index - 1)

    fun onMoveImageLater(index: Int) = moveImage(index, index + 1)

    /**
     * Removes a photograph, and throws away the file behind it.
     *
     * No confirmation. A staged photograph has not been uploaded and
     * costs a re-take rather than a loss, and a dialog on every removal
     * would be paid for on every deliberate one — which is the common
     * case, because rejecting a bad shot is what this button is for.
     */
    fun onRemoveImage(index: Int) {
        val removed = _uiState.value.form.images.getOrNull(index) ?: return

        updateForm({ form -> form.copy(images = form.images.filterNot { it.key == removed.key }) })

        when (removed) {
            // Ours to delete, and never uploaded — a re-take, not a loss.
            is FormPhoto.Staged -> viewModelScope.launch { stagedImages.discard(removed.localUri) }

            // Already in the bucket. Its object is **not** deleted here:
            // nothing is committed until Save, and removing a photograph
            // then backing out of the form must not have destroyed it.
            // The save records it as abandoned; `clearAbandoned` removes it.
            is FormPhoto.Stored -> removedStored += removed.storagePath
        }
    }

    fun onCameraUnavailable() =
        _uiState.update { it.copy(photoProblem = PhotoProblem.NoCameraApp) }

    fun onGalleryUnavailable() =
        _uiState.update { it.copy(photoProblem = PhotoProblem.NoGalleryApp) }

    /**
     * Saves the piece — or carries on saving it, if a previous attempt
     * stopped part-way.
     *
     * There is no separate retry function, deliberately. A retry that
     * takes a different code path from the first attempt is a second
     * implementation of the same thing, and the interesting bugs live in
     * the difference between them. So this reads [progress] to see what
     * has already been done and does only the rest — which on a first
     * attempt is all of it.
     */
    fun save() {
        val current = _uiState.value
        // Two taps must not be two products. Every in-flight state counts,
        // and since M7.9 that guard is a second line rather than the only
        // one: the product id is fixed per attempt, so even a save that
        // somehow started twice would insert the same row twice and the
        // database would recognise it.
        if (current.saveState.inFlight) return

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

        val mode = current.mode
        val attempt = progress ?: SaveProgress(
            // Editing writes to the row that already exists, and its
            // photographs go under `products/{that id}/…`.
            productId = when (mode) {
                is FormMode.Editing -> mode.productId
                is FormMode.Adding -> UUID.randomUUID().toString()
            },
            // Already public. So an interruption part-way through an edit
            // says so, rather than claiming nothing reached the catalogue.
            slug = (mode as? FormMode.Editing)?.slug,
        ).also { progress = it }
        val images = current.form.images

        _uiState.update {
            it.copy(
                problems = problems,
                saveState = SaveState.Uploading(attempt.uploaded.size, images.size),
                // Photographs carried over from an interrupted attempt are
                // shown finished from the outset, because they are.
                uploadProgress = attempt.uploaded.associate { u -> u.localUri to 1f },
            )
        }

        viewModelScope.launch { runSave(categoryId) }
    }

    /**
     * Undoes an interrupted save: the objects out of the bucket, and the
     * row out of the catalogue if one was written.
     *
     * The objects go first. If the row were deleted first and the object
     * delete then failed, what is left is orphaned storage that nothing
     * remembers — whereas a row whose objects are gone is still on the
     * screen, still recorded in [progress], and still discardable.
     */
    fun discard() {
        val attempt = progress ?: return
        val interrupted = _uiState.value.saveState as? SaveState.Interrupted ?: return

        _uiState.update { it.copy(saveState = SaveState.Discarding) }

        viewModelScope.launch {
            val objects = attempt.orphanedPaths()
            val removed = productImageRepository.remove(objects)
            if (removed is RemoveImagesResult.Failed) {
                progress = attempt.copy(abandoned = objects, uploaded = emptyList())
                _uiState.update {
                    it.copy(saveState = interrupted.copy(discardFailure = removed.failure))
                }
                return@launch
            }

            if (attempt.slug != null) {
                val deleted = productRepository.delete(attempt.productId)
                if (deleted is DeleteProductResult.Failed) {
                    // The objects are gone, so a second attempt has only
                    // the row left to remove.
                    progress = attempt.copy(uploaded = emptyList(), abandoned = emptyList())
                    _uiState.update {
                        it.copy(
                            saveState = interrupted.copy(
                                uploaded = 0,
                                discardFailure = deleted.failure,
                            ),
                        )
                    }
                    return@launch
                }
            }

            // A fresh attempt gets a fresh product id. The form and the
            // staged photographs are untouched: discarding undoes what
            // reached the server, not what the owner typed.
            //
            // The draft row goes, because it is keyed by the id that has
            // just been abandoned. Its photographs do **not** — they are
            // still on the form, and the next interruption writes a fresh
            // draft that retains the same files untouched.
            draftRepository.delete(attempt.productId)
            progress = null
            _uiState.update { it.copy(saveState = SaveState.Idle, uploadProgress = emptyMap()) }
        }
    }

    /**
     * The pipeline, in the order that makes an interruption harmless.
     *
     * **Photographs first, the `products` row last.** The obvious order is
     * the other way round — the row exists, so the images have something
     * to attach to — and it is what M7.7 shipped. The trouble is what
     * happens when the connection dies half-way: there is now a piece in
     * the catalogue with two of its five photographs, and undoing it needs
     * a `DELETE` over the same connection that just failed. Compensation
     * cannot be relied on to run at the moment it is most needed.
     *
     * Written this way round, an interrupted save leaves objects in a
     * bucket that nothing points at and **no row at all**, so nothing a
     * customer can reach is ever half-made. That is what M7.9's *Done
     * when* asks for, and it holds without the network's cooperation.
     *
     * The cost is that a name collision or a rejected field is discovered
     * after the uploads rather than before. Acceptable: M7.2 mirrors every
     * constraint the database holds, so a rejection here is close to
     * unreachable — and the photographs are kept, so fixing the name and
     * saving again does not send them twice.
     */
    private suspend fun runSave(categoryId: String) {
        val attempt = progress ?: return

        if (!clearAbandoned(attempt)) return
        val uploaded = uploadRemaining(attempt) ?: return
        val slug = writeRow(categoryId) ?: return
        writeImageRows(uploaded, slug)
    }

    /**
     * Deletes the photographs the owner took off a piece that is **already
     * published**, once the rows that replaced them have been committed.
     *
     * ── Why this is not part of `clearAbandoned` ─────────────────────
     * It was, and that was a bug. `clearAbandoned` runs before the
     * uploads, so a removed photograph left Storage while its
     * `product_images` row still pointed at it — and if anything after
     * that failed (the connection dropping mid-upload is the ordinary
     * case) the save stopped with the row intact and the object gone.
     * The website then served a broken image on a live product page,
     * which is the one outcome the pipeline's ordering exists to prevent.
     *
     * Deleting after the commit inverts the failure: what is left is an
     * object nothing points at. That costs storage rather than showing a
     * customer a broken picture, and it is the same trade the delete
     * pipeline in M8.4 already makes.
     *
     * A failure here is deliberately **not** an interruption. The piece
     * saved; telling the owner otherwise would have them retry something
     * that already worked.
     */
    private suspend fun clearReplacedStored() {
        if (removedStored.isEmpty()) return

        val paths = removedStored.toList()
        if (productImageRepository.remove(paths) is RemoveImagesResult.Removed) {
            removedStored.clear()
        }
    }

    /**
     * Creates the row, or updates the one being edited.
     *
     * The only step of the pipeline that differs between the two modes —
     * which is why editing is a branch here rather than a second pipeline.
     * Everything around it (clearing abandoned objects, uploading what is
     * new, writing the image rows in the on-screen order) is identical,
     * and a second copy of it is how the two start disagreeing.
     */
    private suspend fun writeRow(categoryId: String): String? =
        when (val mode = _uiState.value.mode) {
            is FormMode.Editing -> updateRow(mode, categoryId)
            is FormMode.Adding -> createRow(categoryId)
        }

    /**
     * Removes objects from earlier attempts that the current list no
     * longer wants — a photograph uploaded, then taken off the form.
     *
     * Returns false when the save should stop. A failure here is reported
     * rather than ignored, because carrying on would save the piece and
     * leave paid-for storage that nothing will ever look at again.
     */
    private suspend fun clearAbandoned(attempt: SaveProgress): Boolean {
        val wanted = _uiState.value.form.images
            .filterIsInstance<FormPhoto.Staged>()
            .map { it.localUri }
            .toSet()
        // Only objects this save put in the bucket and no longer wants.
        // Nothing points at them, so removing them now cannot break a
        // page. Photographs taken off an already-published piece are
        // *not* here — see clearReplacedStored for why they wait.
        val abandoned = attempt.abandoned +
            attempt.uploaded.filterNot { it.localUri in wanted }.map { it.storagePath }

        if (abandoned.isEmpty()) return true

        val kept = attempt.uploaded.filter { it.localUri in wanted }

        return when (val removed = productImageRepository.remove(abandoned)) {
            is RemoveImagesResult.Removed -> {
                progress = attempt.copy(uploaded = kept, abandoned = emptyList())
                true
            }

            is RemoveImagesResult.Failed -> {
                progress = attempt.copy(uploaded = kept, abandoned = abandoned)
                interrupt(removed.failure)
                false
            }
        }
    }

    /**
     * Uploads the photographs not already in the bucket, in the order the
     * owner arranged them, and returns the complete set as rows.
     *
     * **Sequential, not concurrent.** Three uploads at once would finish
     * sooner on a good connection, but this app's connection is Indian
     * mobile data: parallel streams there share the same narrow pipe,
     * each one slower, and a stall takes all three with it. Sequential
     * also means a failure has an unambiguous answer to "which ones
     * landed", which is what the retry depends on. M7.13 measures whether
     * this meets the thirty-second target and is the place to revisit it
     * with a number rather than an opinion.
     *
     * Returns null when a photograph failed and the save has stopped.
     */
    private suspend fun uploadRemaining(attempt: SaveProgress): List<UploadedImage>? {
        val images = _uiState.value.form.images

        images.forEach { photo ->
            // Already in the bucket — from a previous attempt, or because
            // this is an edit and it was there before the owner opened
            // the form. Either way it must not be sent again.
            if (photo is FormPhoto.Stored) return@forEach
            val localUri = (photo as FormPhoto.Staged).localUri
            if (progress?.landed(localUri) != null) return@forEach

            val result = productImageRepository.upload(attempt.productId, localUri) { sent, total ->
                // Called from whichever thread is writing the request
                // body. `update` is atomic, which is why the progress map
                // lives in the state flow rather than beside it.
                val fraction = if (total > 0) sent.toFloat() / total else 0f
                _uiState.update { state ->
                    state.copy(uploadProgress = state.uploadProgress + (localUri to fraction))
                }
            }

            when (result) {
                is UploadImageResult.Failed -> {
                    interrupt(result.failure)
                    return null
                }

                is UploadImageResult.Uploaded -> {
                    record(
                        StagedUpload(
                            localUri = localUri,
                            storagePath = result.storagePath,
                            url = result.url,
                            portrait = stagedImages.isPortrait(localUri),
                        ),
                    )

                    _uiState.update { state ->
                        state.copy(
                            // Pinned to 1f: the last progress callback
                            // can arrive a little short of the total, and
                            // a bar that stops at 98% on a photograph
                            // that is finished reads as a stall.
                            uploadProgress = state.uploadProgress + (localUri to 1f),
                            saveState = SaveState.Uploading(
                                completed = progress?.uploaded?.size ?: 0,
                                total = images.size,
                            ),
                        )
                    }
                }
            }
        }

        val landed = progress ?: return null
        // The index in the list the owner is looking at, which is what
        // makes M7.5's promise true: position 0 is the primary image
        // because it is the one at the top of the screen. Assigned here
        // rather than at upload time, so a photograph promoted between two
        // attempts — or between two sessions of editing — still lands
        // where the owner put it.
        return images.mapIndexedNotNull { index, photo ->
            when (photo) {
                is FormPhoto.Stored -> UploadedImage(
                    storagePath = photo.storagePath,
                    url = photo.url,
                    displayOrder = index,
                    // Read back with the row and carried through the form,
                    // not re-derived: the file has not changed, and
                    // measuring it again would need a download. Defaulting
                    // it instead is what silently re-cropped a portrait
                    // piece to a square on the next unrelated edit.
                    portrait = photo.portrait,
                )

                is FormPhoto.Staged -> landed.landed(photo.localUri)?.toRow(index)
            }
        }
    }

    /** Returns the slug, or null when the save has stopped. */
    private suspend fun createRow(categoryId: String): String? {
        val attempt = progress ?: return null
        // Already written by an earlier attempt whose image rows failed.
        attempt.slug?.let { return it }

        _uiState.update { it.copy(saveState = SaveState.Saving) }

        val draft = _uiState.value.form.toDraft(categoryId)
        return when (val result = productRepository.create(attempt.productId, draft)) {
            is CreateProductResult.Created -> {
                progress = attempt.copy(slug = result.slug)
                result.slug
            }

            is CreateProductResult.SlugExhausted -> {
                _uiState.update { it.copy(saveState = SaveState.NameUnavailable) }
                null
            }

            is CreateProductResult.Failed -> {
                interrupt(result.failure)
                null
            }
        }
    }

    private suspend fun writeImageRows(images: List<UploadedImage>, slug: String) {
        val productId = progress?.productId ?: return
        _uiState.update { it.copy(saveState = SaveState.Saving) }

        when (val written = productImageRepository.replaceImages(productId, images)) {
            is WriteImagesResult.Written -> {
                // After the commit, never before: the rows that pointed at
                // these objects have just been replaced.
                clearReplacedStored()
                progress = null
                clearDraft(productId)
                _uiState.update {
                    it.copy(saveState = SaveState.Saved(it.form.name.trim(), slug))
                }
            }

            // The row is in the catalogue and its photographs are not on
            // it. The only failure that reaches this with `inCatalogue`
            // true, and the one place the owner has to be told the piece
            // is already public.
            is WriteImagesResult.Failed -> interrupt(written.failure)
        }
    }

    /**
     * Writes the changed fields. Returns the slug it already had — editing
     * a name does not move the piece's address on the website.
     */
    private suspend fun updateRow(mode: FormMode.Editing, categoryId: String): String? {
        _uiState.update { it.copy(saveState = SaveState.Saving) }

        return when (
            val result = productRepository.update(
                mode.productId,
                _uiState.value.form.toDraft(categoryId),
            )
        ) {
            is UpdateProductResult.Updated -> mode.slug

            // Deleted from another device between opening the form and
            // saving it. Not a failure to retry — there is nothing left to
            // write to, and the form's contents are the only copy.
            is UpdateProductResult.Missing -> {
                _uiState.update { it.copy(loadState = LoadState.Missing) }
                null
            }

            is UpdateProductResult.Failed -> {
                interrupt(result.failure)
                null
            }
        }
    }

    /** Stops the save where it is, in terms the screen can act on. */
    private fun interrupt(failure: RequestFailure) {
        val attempt = progress
        _uiState.update {
            it.copy(
                saveState = SaveState.Interrupted(
                    uploaded = attempt?.uploaded?.size ?: 0,
                    total = it.form.images.size,
                    inCatalogue = attempt?.slug != null,
                    failure = failure,
                ),
            )
        }

        keepDraft(failure)
    }

    /**
     * Writes the piece to the device so leaving this screen cannot lose
     * it (M8.9).
     *
     * ── Why an interruption is the trigger ───────────────────────────
     * Not every keystroke: the `SavedStateHandle` already carries the
     * form across process death *while the screen is alive*, and a draft
     * row per keystroke would be a write per letter. What that handle
     * cannot do is survive the owner walking away from the form — and the
     * moment that matters is the one where a save has just failed, which
     * is precisely when they are most likely to give up and close the
     * app.
     *
     * ── Two cases deliberately excluded ──────────────────────────────
     * **Editing.** The piece is already in the catalogue; there is
     * nothing waiting to be uploaded, and a "pending" entry for it would
     * be a second copy of something that exists.
     *
     * **A save whose row was written.** Same reason: it is public. Its
     * photographs are what is unfinished, and the screen already says so
     * and offers to carry on.
     */
    private fun keepDraft(failure: RequestFailure) {
        val attempt = progress ?: return
        if (attempt.slug != null) return
        if (_uiState.value.mode !is FormMode.Adding) return

        // validateCategory ran before the save started, so this holds —
        // re-read rather than asserted, per CLAUDE.md's rule on `!!`.
        val categoryId = _uiState.value.form.categoryId ?: return

        viewModelScope.launch {
            val photos = retainPhotos()

            draftRepository.save(
                PendingDraft(
                    productId = attempt.productId,
                    draft = _uiState.value.form.toDraft(categoryId),
                    photoUris = photos,
                    // Read after retainPhotos, which rewrites these to
                    // the moved URIs. Carried so M8.10's sync sends only
                    // what is missing rather than everything again.
                    uploaded = progress?.uploaded.orEmpty(),
                    savedAt = System.currentTimeMillis(),
                    failure = failure,
                ),
            )
        }
    }

    /**
     * Moves the form's photographs out of the reclaimable cache and
     * rewrites every reference to them.
     *
     * The rewrite is the part that is easy to miss. [SaveProgress]
     * remembers which staged URI each uploaded object came from, and
     * `clearAbandoned` treats an uploaded object whose URI is no longer
     * on the form as abandoned — so moving the files without remapping
     * that record would delete every photograph already in Storage and
     * send it again.
     */
    private suspend fun retainPhotos(): List<String> {
        val moved = buildMap {
            _uiState.value.form.images.filterIsInstance<FormPhoto.Staged>().forEach { photo ->
                val retained = stagedImages.retain(photo.localUri)
                if (retained != null && retained != photo.localUri) put(photo.localUri, retained)
            }
        }

        if (moved.isNotEmpty()) {
            _uiState.update { state ->
                val form = state.form.copy(
                    images = state.form.images.map { photo ->
                        when (photo) {
                            is FormPhoto.Staged ->
                                moved[photo.localUri]?.let { FormPhoto.Staged(it) } ?: photo

                            is FormPhoto.Stored -> photo
                        }
                    },
                )
                persist(form)
                state.copy(form = form)
            }

            progress = progress?.let { attempt ->
                attempt.copy(
                    uploaded = attempt.uploaded.map { upload ->
                        moved[upload.localUri]
                            ?.let { upload.copy(localUri = it) }
                            ?: upload
                    },
                )
            }
        }

        return _uiState.value.form.images
            .filterIsInstance<FormPhoto.Staged>()
            .map { it.localUri }
    }

    /**
     * The piece is in the catalogue, so it is not waiting for anything.
     *
     * Its retained photographs go too: they are in Storage now, and files
     * under `files/drafts/` are this app's to delete — unlike the cache,
     * nothing else will ever reclaim them.
     */
    private suspend fun clearDraft(productId: String) {
        val kept = draftRepository.byId(productId) ?: return
        draftRepository.delete(productId)
        kept.photoUris.forEach { stagedImages.discard(it) }
    }

    private fun record(upload: StagedUpload) {
        progress = progress?.let { it.copy(uploaded = it.uploaded + upload) }
    }

    /** Lets the screen dismiss an error without re-entering everything. */
    fun onErrorDismissed() = _uiState.update { it.copy(saveState = SaveState.Idle) }

    /**
     * [clearProblem] clears only the problem belonging to the field that
     * changed. A message about a field the owner has not touched is
     * still true, and wiping all of them on every keystroke would hide
     * what Save just told them.
     */
    private fun moveImage(from: Int, to: Int) {
        val images = _uiState.value.form.images
        // The ends are guarded here rather than by the caller, so a stale
        // index from a recomposition can never reorder the wrong pair.
        if (from !in images.indices || to !in images.indices) return

        updateForm({ form ->
            form.copy(images = form.images.toMutableList().apply { add(to, removeAt(from)) })
        })
    }

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
                // being true the moment the form changes. An attempt still
                // running is a different matter — it is not describing the
                // past, and clearing it would drop the double-tap guard
                // and hide a progress bar mid-upload.
                saveState = if (state.saveState.inFlight) state.saveState else SaveState.Idle,
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
        // JSON, since M8.3b made these a sealed type. A Bundle can hold an
        // ArrayList of strings and nothing richer, and four parallel lists
        // is how the fourth ends up a different length from the others —
        // the same argument SaveProgress makes.
        savedState[KEY_IMAGES] = Json.encodeToString(form.images)
    }

    private fun restoreForm() = ProductForm(
        name = savedState[KEY_NAME] ?: "",
        categoryId = savedState[KEY_CATEGORY],
        purityId = savedState[KEY_PURITY],
        weight = savedState[KEY_WEIGHT] ?: "",
        description = savedState[KEY_DESCRIPTION] ?: "",
        tags = savedState[KEY_TAGS] ?: "",
        featured = savedState[KEY_FEATURED] ?: false,
        images = savedState.get<String>(KEY_IMAGES)
            ?.let { runCatching { Json.decodeFromString<List<FormPhoto>>(it) }.getOrNull() }
            ?: emptyList(),
    )

    /**
     * Stored as JSON rather than as fields, because a `Bundle` has no way
     * to hold a list of records and spreading four parallel `ArrayList`s
     * across the handle is how the fourth one ends up a different length
     * from the other three.
     *
     * A value that will not parse is treated as no value. It should not
     * happen; if it does, the alternative is a form that cannot open, and
     * the cost of being wrong here is orphaned objects rather than a lost
     * piece.
     */
    private fun restoreProgress(): SaveProgress? =
        savedState.get<String>(KEY_PROGRESS)?.let { stored ->
            runCatching { Json.decodeFromString<SaveProgress>(stored) }.getOrNull()
        }

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
        const val KEY_PROGRESS = "save_progress"

        /** The navigation route's argument name. See ui/navigation/Destinations.kt. */
        const val KEY_PRODUCT_ID = "productId"
    }
}

/**
 * Form text to a draft.
 *
 * Tags are comma-separated because that is how someone types a list on a
 * phone keyboard without a chip editor to fight. Blank entries are
 * dropped and each is trimmed, so `"bridal, temple,"` is two tags.
 */
/**
 * A stored piece back into form text — the inverse of [toDraft].
 *
 * Tags rejoin with ", " rather than "," because that is what the field's
 * hint asks for and what the owner would have typed; splitting trims, so
 * the round trip is stable.
 *
 * A null weight becomes blank, not "null" or "0.0": the field's own rule
 * is that empty means "not weighed", and a zero would fail the positive
 * constraint M7.2 mirrors.
 */
/** A stored photograph as the form holds it. */
internal fun asFormPhoto(photo: StoredPhoto) = FormPhoto.Stored(
    storagePath = photo.storagePath,
    url = photo.url,
    portrait = photo.portrait,
)

internal fun ProductDraft.toForm() = ProductForm(
    name = name,
    categoryId = categoryId,
    purityId = purityId,
    // `toString` on a Double gives "48.6" but also "48.0" for a whole
    // number. Trimming the tail keeps what the owner typed recognisable.
    weight = weightGrams?.let { grams ->
        if (grams == grams.toLong().toDouble()) grams.toLong().toString() else grams.toString()
    } ?: "",
    description = description.orEmpty(),
    tags = tags.joinToString(", "),
    featured = featured,
)

internal fun ProductForm.toDraft(categoryId: String) = ProductDraft(
    name = name.trim(),
    categoryId = categoryId,
    purityId = purityId,
    weightGrams = weight.trim().toDoubleOrNull(),
    description = description,
    tags = tags.split(',').map(String::trim).filter(String::isNotEmpty),
    featured = featured,
)
