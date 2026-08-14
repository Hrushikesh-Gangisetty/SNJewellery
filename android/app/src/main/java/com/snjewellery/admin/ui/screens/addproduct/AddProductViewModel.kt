package com.snjewellery.admin.ui.screens.addproduct

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snjewellery.admin.domain.RequestFailure
import com.snjewellery.admin.domain.catalogue.CatalogueRepository
import com.snjewellery.admin.domain.catalogue.CatalogueResult
import com.snjewellery.admin.domain.catalogue.Category
import com.snjewellery.admin.domain.catalogue.Purity
import com.snjewellery.admin.domain.media.StagedImages
import com.snjewellery.admin.domain.product.CreateProductResult
import com.snjewellery.admin.domain.product.DeleteProductResult
import com.snjewellery.admin.domain.product.FieldProblem
import com.snjewellery.admin.domain.product.ProductDraft
import com.snjewellery.admin.domain.product.ProductFormRules
import com.snjewellery.admin.domain.product.ProductImageRepository
import com.snjewellery.admin.domain.product.ProductRepository
import com.snjewellery.admin.domain.product.RemoveImagesResult
import com.snjewellery.admin.domain.product.UploadImageResult
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
 * One staged photograph that is now in the bucket.
 *
 * Not [UploadedImage], which is the row payload and carries a
 * `display_order`. That is deliberately **not** recorded here: the order
 * is the index in the list on screen at the moment the rows are written,
 * so a photograph promoted between two attempts still lands where the
 * owner put it. What this remembers is only where the object went and
 * which staged file it came from.
 */
@Serializable
internal data class StagedUpload(
    val localUri: String,
    val storagePath: String,
    val url: String,
    val portrait: Boolean,
) {
    fun toRow(displayOrder: Int) = UploadedImage(
        storagePath = storagePath,
        url = url,
        displayOrder = displayOrder,
        portrait = portrait,
    )
}

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
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddProductUiState(form = restoreForm()))
    val uiState: StateFlow<AddProductUiState> = _uiState.asStateFlow()

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
                if (staged == null) failed++ else updateForm({ it.copy(images = it.images + staged) })
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

        updateForm({ it.copy(images = it.images - removed) })
        viewModelScope.launch { stagedImages.discard(removed) }
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

        val attempt = progress
            ?: SaveProgress(productId = UUID.randomUUID().toString()).also { progress = it }
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
        val slug = createRow(categoryId) ?: return
        writeImageRows(uploaded, slug)
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
        val wanted = _uiState.value.form.images.toSet()
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

        images.forEach { localUri ->
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
        // attempts still lands where the owner put it.
        return images.mapIndexedNotNull { index, uri -> landed.landed(uri)?.toRow(index) }
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
                progress = null
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
