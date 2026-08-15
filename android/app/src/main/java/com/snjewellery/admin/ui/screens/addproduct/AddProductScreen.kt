package com.snjewellery.admin.ui.screens.addproduct

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MenuAnchorType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snjewellery.admin.R
import com.snjewellery.admin.domain.RequestFailure
import com.snjewellery.admin.domain.catalogue.Category
import com.snjewellery.admin.domain.catalogue.Purity
import com.snjewellery.admin.domain.product.FieldProblem
import com.snjewellery.admin.ui.theme.SnTheme
import com.snjewellery.admin.ui.theme.Tokens
import com.snjewellery.admin.ui.theme.snTextStyles

/**
 * Add Product — the screen the shop actually bought this platform for.
 *
 * Every field the PRD's Add Product section names, in its order, and
 * since M7.3 the photographs too. Compression and the thirty-second
 * upload pipeline follow in M7.6–M7.7; what exists here writes the
 * `products` row.
 *
 * ── Built for one hand, between customers ────────────────────────────
 * The form scrolls with `imePadding`, so the keyboard never covers the
 * field being typed into or the Save button. Category and Purity are
 * dropdowns rather than free text because the owner picking from a list
 * cannot misspell a category into a new one — and because both are
 * lookup tables the owner controls, read live rather than hard-coded.
 */
@Composable
fun AddProductScreen(
    onSaved: (name: String, slug: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddProductViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Built here rather than inside the form: activity result launchers
    // need an ActivityResultRegistryOwner, which a @Preview has none of.
    // See ProductImages.kt.
    val photos = rememberPhotoSources(
        onNewCaptureTarget = viewModel::newCaptureTarget,
        onCaptured = viewModel::onCaptureFinished,
        onChosen = viewModel::onGallerySelection,
        onCameraRefused = viewModel::onCameraPermissionRefused,
        onCameraUnavailable = viewModel::onCameraUnavailable,
        onGalleryUnavailable = viewModel::onGalleryUnavailable,
    )

    AddProductScreen(
        uiState = uiState,
        onNameChange = viewModel::onNameChange,
        onCategoryChange = viewModel::onCategoryChange,
        onPurityChange = viewModel::onPurityChange,
        onWeightChange = viewModel::onWeightChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onTagsChange = viewModel::onTagsChange,
        onFeaturedChange = viewModel::onFeaturedChange,
        onNameBlur = viewModel::onNameBlur,
        onWeightBlur = viewModel::onWeightBlur,
        onSave = viewModel::save,
        onDiscard = viewModel::discard,
        onRetryOptions = viewModel::loadOptions,
        onRetryLoad = viewModel::retryLoad,
        onTakePhoto = photos.takePhoto,
        onChoosePhotos = photos.choosePhotos,
        onOpenCameraSettings = photos.openSettings,
        onMoveImageEarlier = viewModel::onMoveImageEarlier,
        onMoveImageLater = viewModel::onMoveImageLater,
        onRemoveImage = viewModel::onRemoveImage,
        onSaved = onSaved,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddProductScreen(
    uiState: AddProductUiState,
    modifier: Modifier = Modifier,
    onNameChange: (String) -> Unit = {},
    onCategoryChange: (String) -> Unit = {},
    onPurityChange: (String?) -> Unit = {},
    onWeightChange: (String) -> Unit = {},
    onDescriptionChange: (String) -> Unit = {},
    onTagsChange: (String) -> Unit = {},
    onFeaturedChange: (Boolean) -> Unit = {},
    onNameBlur: () -> Unit = {},
    onWeightBlur: () -> Unit = {},
    onSave: () -> Unit = {},
    onDiscard: () -> Unit = {},
    onRetryOptions: () -> Unit = {},
    onRetryLoad: () -> Unit = {},
    onTakePhoto: () -> Unit = {},
    onChoosePhotos: () -> Unit = {},
    onOpenCameraSettings: () -> Unit = {},
    onMoveImageEarlier: (Int) -> Unit = {},
    onMoveImageLater: (Int) -> Unit = {},
    onRemoveImage: (Int) -> Unit = {},
    onSaved: (name: String, slug: String) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val saveState = uiState.saveState

    if (saveState is SaveState.Saved) {
        LaunchedEffect(saveState.slug) { onSaved(saveState.name, saveState.slug) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (uiState.mode is FormMode.Editing) {
                                R.string.edit_product_title
                            } else {
                                R.string.add_product_title
                            },
                        ),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(
                            text = stringResource(R.string.add_product_back),
                            style = MaterialTheme.snTextStyles.label,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        when (val load = uiState.loadState) {
            // Editing only. Adding is Ready from the first frame.
            is LoadState.Loading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            // Deleted from another device. Retrying cannot fix it, so the
            // only offer is back to the list.
            is LoadState.Missing -> Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(Tokens.Space.s4),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
            ) {
                Text(
                    text = stringResource(R.string.edit_product_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(
                    onClick = onBack,
                    modifier = Modifier.heightIn(min = Tokens.Layout.touchTarget),
                ) {
                    Text(stringResource(R.string.edit_product_back_to_list))
                }
            }

            is LoadState.Failed -> Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(Tokens.Space.s4),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
            ) {
                ErrorText(
                    if (load.failure.offline) {
                        stringResource(R.string.edit_product_offline)
                    } else {
                        stringResource(
                            R.string.edit_product_load_error,
                            load.failure.detail
                                ?: stringResource(R.string.add_product_error_no_detail),
                        )
                    },
                )
                Button(
                    onClick = onRetryLoad,
                    modifier = Modifier.heightIn(min = Tokens.Layout.touchTarget),
                ) {
                    Text(stringResource(R.string.add_product_options_retry))
                }
            }

            is LoadState.Ready -> Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
        ) {
            OutlinedTextField(
                value = uiState.form.name,
                onValueChange = onNameChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .validateOnBlur(onNameBlur),
                label = { Text(stringResource(R.string.add_product_name)) },
                isError = uiState.problems.name != null,
                supportingText = uiState.problems.name?.let { problem ->
                    { Text(stringResource(problem.messageRes())) }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )

            when (val options = uiState.options) {
                is OptionsState.Loading -> Text(
                    text = stringResource(R.string.add_product_options_loading),
                    style = MaterialTheme.snTextStyles.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                is OptionsState.Failed -> OptionsError(
                    failure = options.failure,
                    onRetry = onRetryOptions,
                )

                is OptionsState.Loaded -> {
                    CategoryPicker(
                        categories = options.categories,
                        selectedId = uiState.form.categoryId,
                        problem = uiState.problems.category,
                        onSelect = onCategoryChange,
                    )
                    PurityPicker(
                        purities = options.purities,
                        selectedId = uiState.form.purityId,
                        onSelect = onPurityChange,
                    )
                }
            }

            OutlinedTextField(
                value = uiState.form.weight,
                onValueChange = onWeightChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .validateOnBlur(onWeightBlur),
                label = { Text(stringResource(R.string.add_product_weight)) },
                isError = uiState.problems.weight != null,
                supportingText = uiState.problems.weight?.let { problem ->
                    { Text(stringResource(problem.messageRes())) }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next,
                ),
            )

            OutlinedTextField(
                value = uiState.form.description,
                onValueChange = onDescriptionChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.add_product_description)) },
                minLines = DESCRIPTION_LINES,
            )

            OutlinedTextField(
                value = uiState.form.tags,
                onValueChange = onTagsChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.add_product_tags)) },
                supportingText = { Text(stringResource(R.string.add_product_tags_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Tokens.Layout.touchTarget),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.add_product_featured),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Switch(checked = uiState.form.featured, onCheckedChange = onFeaturedChange)
            }

            // After Featured and before Save, which is the PRD's own
            // order for this form.
            ProductImages(
                images = uiState.form.images,
                photoProblem = uiState.photoProblem,
                addingPhotos = uiState.addingPhotos,
                uploadProgress = uiState.uploadProgress,
                onTakePhoto = onTakePhoto,
                onChoosePhotos = onChoosePhotos,
                onOpenSettings = onOpenCameraSettings,
                onMoveEarlier = onMoveImageEarlier,
                onMoveLater = onMoveImageLater,
                onRemove = onRemoveImage,
            )

            Button(
                onClick = {
                    keyboard?.hide()
                    onSave()
                },
                // Enabled whenever a save is not already in flight, even
                // with fields missing. Disabling it would hide *why*
                // nothing happens when it is pressed; pressing it puts
                // the reason under the field that needs it. Same rule as
                // the login screen.
                enabled = !saveState.inFlight,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Tokens.Layout.touchTarget),
            ) {
                when (saveState) {
                    is SaveState.Saving, is SaveState.Discarding -> CircularProgressIndicator(
                        modifier = Modifier.size(Tokens.Space.s5),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )

                    // The overall figure. The per-image bars are in the
                    // Photographs section above; together they are what
                    // M7.7 asks for.
                    is SaveState.Uploading -> Text(
                        stringResource(
                            R.string.add_product_uploading,
                            saveState.completed + 1,
                            saveState.total,
                        ),
                    )

                    // Carrying on is the same button doing the same thing,
                    // so it is not a second control — only a word that
                    // says the photographs already up will not go again.
                    is SaveState.Interrupted -> Text(
                        stringResource(
                            if (saveState.uploaded > 0 || saveState.inCatalogue) {
                                R.string.add_product_save_continue
                            } else {
                                R.string.add_product_save
                            },
                        ),
                    )

                    else -> Text(
                        stringResource(
                            if (uiState.mode is FormMode.Editing) {
                                R.string.edit_product_save
                            } else {
                                R.string.add_product_save
                            },
                        ),
                    )
                }
            }

            SaveError(saveState, onDiscard = onDiscard)
            }
        }
    }
}

/**
 * What went wrong, and what to do about it.
 *
 * Every recoverable failure says the same two things — whether the piece
 * is in the catalogue, and that Save carries on rather than starting
 * again — because that is what stops the owner either giving up on a
 * piece or entering it twice.
 */
@Composable
private fun SaveError(saveState: SaveState, onDiscard: () -> Unit) {
    if (saveState is SaveState.NameUnavailable) {
        ErrorText(stringResource(R.string.add_product_error_name_taken))
        return
    }

    val interrupted = saveState as? SaveState.Interrupted ?: return

    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        val failure = interrupted.failure

        ErrorText(
            when {
                // The piece is public and its photographs are not on it.
                // The one case where "nothing was saved" would be a lie,
                // and the only one that says so.
                interrupted.inCatalogue -> stringResource(R.string.add_product_error_rows)

                // Nothing failed: the app was reclaimed while the upload
                // was running. Borrowing "check your signal" here would
                // send the owner looking for a problem that was never
                // theirs.
                failure == null -> stringResource(
                    R.string.add_product_error_reopened,
                    interrupted.uploaded,
                    interrupted.total,
                )

                // Nothing reached the server at all: no photograph landed
                // and no row was written, so this is the plain failure the
                // form has always reported.
                interrupted.uploaded == 0 && !failure.offline -> stringResource(
                    R.string.add_product_error_server,
                    failure.detail ?: stringResource(R.string.add_product_error_no_detail),
                )

                interrupted.uploaded == 0 -> stringResource(R.string.add_product_error_offline)

                else -> stringResource(
                    if (failure.offline) {
                        R.string.add_product_error_images_offline
                    } else {
                        R.string.add_product_error_images_server
                    },
                    interrupted.uploaded,
                    interrupted.total,
                )
            },
        )

        if (interrupted.discardFailure != null) {
            ErrorText(
                if (interrupted.discardFailure.offline) {
                    stringResource(R.string.add_product_discard_offline)
                } else {
                    stringResource(
                        R.string.add_product_discard_error,
                        interrupted.discardFailure.detail
                            ?: stringResource(R.string.add_product_error_no_detail),
                    )
                },
            )
        }

        if (interrupted.canDiscard) {
            TextButton(
                onClick = onDiscard,
                modifier = Modifier.heightIn(min = Tokens.Layout.touchTarget),
            ) {
                Text(stringResource(R.string.add_product_discard))
            }
        }
    }
}

@Composable
private fun ErrorText(message: String) = Text(
    text = message,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.error,
    modifier = Modifier.fillMaxWidth(),
)

@Composable
private fun OptionsError(failure: RequestFailure, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        Text(
            text = if (failure.offline) {
                stringResource(R.string.add_product_options_offline)
            } else {
                stringResource(
                    R.string.add_product_options_error,
                    failure.detail ?: stringResource(R.string.add_product_error_no_detail),
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = Tokens.Layout.touchTarget)) {
            Text(stringResource(R.string.add_product_options_retry))
        }
    }
}

/**
 * Categories, in the owner's own `display_order`.
 *
 * A hidden category is offered and **labelled**, not withheld: filing a
 * piece into one before a launch is what hidden categories are for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPicker(
    categories: List<Category>,
    selectedId: String?,
    problem: FieldProblem?,
    onSelect: (String) -> Unit,
) {
    val selected = categories.firstOrNull { it.id == selectedId }
    Picker(
        label = stringResource(R.string.add_product_category),
        selectedLabel = selected?.let { it.displayName() } ?: "",
        problem = problem,
    ) { dismiss ->
        categories.forEach { category ->
            DropdownMenuItem(
                text = { Text(category.displayName()) },
                onClick = {
                    onSelect(category.id)
                    dismiss()
                },
            )
        }
    }
}

@Composable
private fun Category.displayName(): String = if (isVisible) {
    name
} else {
    stringResource(R.string.add_product_category_hidden, name)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurityPicker(
    purities: List<Purity>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    val selected = purities.firstOrNull { it.id == selectedId }
    val noneLabel = stringResource(R.string.add_product_purity_none)

    Picker(
        label = stringResource(R.string.add_product_purity),
        selectedLabel = selected?.label ?: noneLabel,
    ) { dismiss ->
        // Purity is optional — a silver anklet may have none recorded —
        // so clearing it has to be reachable once something is chosen.
        DropdownMenuItem(
            text = { Text(noneLabel) },
            onClick = {
                onSelect(null)
                dismiss()
            },
        )
        purities.forEach { purity ->
            DropdownMenuItem(
                text = { Text(purity.label) },
                onClick = {
                    onSelect(purity.id)
                    dismiss()
                },
            )
        }
    }
}

/** The shared dropdown shell, so both pickers behave identically. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Picker(
    label: String,
    selectedLabel: String,
    problem: FieldProblem? = null,
    items: @Composable (dismiss: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            isError = problem != null,
            supportingText = problem?.let { { Text(stringResource(it.messageRes())) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items { expanded = false }
        }
    }
}

/**
 * Runs [onBlur] when the field is left — and **only** if it was ever
 * entered.
 *
 * `onFocusChanged` fires once at initial composition with
 * `isFocused = false`, so the naive version validates every field the
 * moment the screen opens: the owner taps Add Product and is immediately
 * told to give the piece a name they have had no chance to type. That is
 * the exact failure the blur rule exists to avoid, and it survives
 * process death too, so a restored form came back covered in errors.
 * Caught on a screenshot, not in review.
 */
@Composable
private fun Modifier.validateOnBlur(onBlur: () -> Unit): Modifier {
    var everFocused by remember { mutableStateOf(false) }
    return onFocusChanged { state ->
        if (state.isFocused) {
            everFocused = true
        } else if (everFocused) {
            onBlur()
        }
    }
}

private fun FieldProblem.messageRes(): Int = when (this) {
    FieldProblem.NameBlank -> R.string.add_product_error_name_blank
    FieldProblem.CategoryMissing -> R.string.add_product_error_category_missing
    FieldProblem.WeightNotANumber -> R.string.add_product_error_weight_nan
    FieldProblem.WeightNotPositive -> R.string.add_product_error_weight_positive
}

private const val DESCRIPTION_LINES = 3

@PreviewLightDark
@Composable
private fun AddProductPreview() {
    SnTheme {
        AddProductScreen(
            uiState = AddProductUiState(
                form = ProductForm(name = "Kundan Choker Set", tags = "kundan, bridal"),
                options = OptionsState.Loaded(
                    categories = listOf(
                        Category("1", "Bridal Jewellery", isVisible = true),
                        Category("2", "Unreleased Collection", isVisible = false),
                    ),
                    purities = listOf(Purity("1", "22K", "22K Gold")),
                ),
            ),
        )
    }
}

@PreviewLightDark
@Composable
private fun AddProductOptionsOfflinePreview() {
    SnTheme {
        AddProductScreen(
            uiState = AddProductUiState(
                options = OptionsState.Failed(RequestFailure(offline = true)),
            ),
        )
    }
}
