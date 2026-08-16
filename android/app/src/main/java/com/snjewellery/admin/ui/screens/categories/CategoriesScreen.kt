package com.snjewellery.admin.ui.screens.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snjewellery.admin.R
import com.snjewellery.admin.domain.RequestFailure
import com.snjewellery.admin.domain.catalogue.Category
import com.snjewellery.admin.ui.theme.SnTheme
import com.snjewellery.admin.ui.theme.Tokens
import com.snjewellery.admin.ui.theme.snTextStyles

/**
 * The categories the whole catalogue is filed under: add, rename, delete.
 *
 * ── One dialog, two jobs ─────────────────────────────────────────────
 * Adding and renaming are the same field with the same rules, and they
 * differ only in what Save does — so they are one dialog rather than two
 * that would have to be kept saying the same thing. Delete lives inside
 * it because a category is deleted by opening it and deciding, not by a
 * control sitting in a list where a mis-tap is expensive.
 *
 * ── Hidden categories are shown, marked ──────────────────────────────
 * The same decision the Add Product picker makes: a category the owner
 * cannot see here is a category they cannot rename or delete. Making one
 * hidden or visible is M8.7's, and belongs on the row.
 */
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    onShowPieces: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CategoriesScreen(
        uiState = uiState,
        onBack = onBack,
        onShowPieces = onShowPieces,
        onRetry = viewModel::load,
        onAddRequested = viewModel::onAddRequested,
        onEditRequested = viewModel::onEditRequested,
        onMoveEarlier = viewModel::onMoveEarlier,
        onMoveLater = viewModel::onMoveLater,
        onVisibilityChange = viewModel::onVisibilityChange,
        onNoticeDismissed = viewModel::onNoticeDismissed,
        onNameChange = viewModel::onNameChange,
        onSave = viewModel::onSave,
        onDeleteRequested = viewModel::onDeleteRequested,
        onDeleteCancelled = viewModel::onDeleteCancelled,
        onDeleteConfirmed = viewModel::onDeleteConfirmed,
        onMissingAcknowledged = viewModel::onMissingAcknowledged,
        onDismissEditor = viewModel::onDismissEditor,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoriesScreen(
    uiState: CategoriesUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onShowPieces: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onAddRequested: () -> Unit = {},
    onEditRequested: (Category) -> Unit = {},
    onMoveEarlier: (Category) -> Unit = {},
    onMoveLater: (Category) -> Unit = {},
    onVisibilityChange: (Boolean) -> Unit = {},
    onNoticeDismissed: () -> Unit = {},
    onNameChange: (String) -> Unit = {},
    onSave: () -> Unit = {},
    onDeleteRequested: () -> Unit = {},
    onDeleteCancelled: () -> Unit = {},
    onDeleteConfirmed: () -> Unit = {},
    onMissingAcknowledged: () -> Unit = {},
    onDismissEditor: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.categories_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(
                            text = stringResource(R.string.categories_back),
                            style = MaterialTheme.snTextStyles.label,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onAddRequested) {
                        Text(
                            text = stringResource(R.string.categories_add),
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
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                uiState.loading -> CategoriesSkeleton()

                uiState.failure != null -> CategoriesError(uiState.failure, onRetry)

                uiState.isEmpty -> EmptyCategories(onAddRequested)

                else -> CategoryList(
                    categories = uiState.categories,
                    reordering = uiState.reordering,
                    onEdit = onEditRequested,
                    onMoveEarlier = onMoveEarlier,
                    onMoveLater = onMoveLater,
                )
            }
        }
    }

    uiState.editor?.let { editor ->
        CategoryDialog(
            editor = editor,
            onNameChange = onNameChange,
            onVisibilityChange = onVisibilityChange,
            onShowPieces = { editor.category?.let { onShowPieces(it.id) } },
            onSave = onSave,
            onDeleteRequested = onDeleteRequested,
            onDeleteCancelled = onDeleteCancelled,
            onDeleteConfirmed = onDeleteConfirmed,
            onMissingAcknowledged = onMissingAcknowledged,
            onDismiss = onDismissEditor,
        )
    }

    // A move the list could not keep. A dialog rather than an inline
    // line, because the order on screen has just been re-read underneath
    // it and the owner needs to know why it moved back.
    uiState.notice?.let { notice ->
        AlertDialog(
            onDismissRequest = onNoticeDismissed,
            title = { Text(stringResource(R.string.categories_reorder_failed_title)) },
            text = { Text(notice.message()) },
            confirmButton = {
                TextButton(onClick = onNoticeDismissed) {
                    Text(stringResource(R.string.categories_notice_dismiss))
                }
            },
        )
    }
}

@Composable
private fun CategoriesNotice.message(): String = when (this) {
    is CategoriesNotice.ReorderMissing -> stringResource(R.string.categories_reorder_missing)
    is CategoriesNotice.ReorderFailed -> if (failure.offline) {
        stringResource(R.string.categories_reorder_offline)
    } else {
        stringResource(
            R.string.categories_reorder_error,
            failure.detail ?: stringResource(R.string.categories_no_detail),
        )
    }
}

@Composable
private fun CategoryList(
    categories: List<Category>,
    reordering: Boolean,
    onEdit: (Category) -> Unit,
    onMoveEarlier: (Category) -> Unit,
    onMoveLater: (Category) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = Tokens.Space.s2),
    ) {
        itemsIndexed(categories, key = { _, category -> category.id }) { index, category ->
            CategoryRow(
                category = category,
                index = index,
                total = categories.size,
                reordering = reordering,
                onClick = { onEdit(category) },
                onMoveEarlier = { onMoveEarlier(category) },
                onMoveLater = { onMoveLater(category) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/**
 * ── Arrows, not a drag ───────────────────────────────────────────────
 * The plan's wording is "drag-to-reorder", and this is the pattern M7.5
 * already chose for the photographs on the Add Product form: two arrows
 * with a spoken description each. A long-press drag has no affordance,
 * cannot be operated by a screen reader, and is the harder gesture to
 * land one-handed over a counter — which is the app's whole context. The
 * *Done when* is about the website's order changing, and that is the same
 * either way.
 */
@Composable
private fun CategoryRow(
    category: Category,
    index: Int,
    total: Int,
    reordering: Boolean,
    onClick: () -> Unit,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = ROW_HEIGHT)
            .padding(start = Tokens.Space.s4, end = Tokens.Space.s2)
            .padding(vertical = Tokens.Space.s2),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = category.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // Marked rather than withheld: a hidden category is still the
        // owner's to rename, and hiding one before a launch is what the
        // flag is for.
        if (!category.isVisible) {
            Text(
                text = stringResource(R.string.categories_hidden),
                style = MaterialTheme.snTextStyles.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(Tokens.Radius.sm),
                    )
                    .padding(horizontal = Tokens.Space.s2, vertical = Tokens.Space.s1),
            )
        }

        // Each says which category it moves: a column of identical "Move
        // up" buttons is unusable with a screen reader.
        IconButton(onClick = onMoveEarlier, enabled = !reordering && index > 0) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = stringResource(
                    R.string.categories_move_earlier,
                    category.name,
                ),
            )
        }
        IconButton(onClick = onMoveLater, enabled = !reordering && index < total - 1) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(
                    R.string.categories_move_later,
                    category.name,
                ),
            )
        }
    }
}

/**
 * Add, rename and delete, in one dialog.
 *
 * The delete confirmation replaces the field rather than sitting under
 * it, so the one irreversible action cannot be tapped while the owner is
 * looking at something else.
 */
@Composable
private fun CategoryDialog(
    editor: CategoryEditor,
    onNameChange: (String) -> Unit,
    onVisibilityChange: (Boolean) -> Unit,
    onShowPieces: () -> Unit,
    onSave: () -> Unit,
    onDeleteRequested: () -> Unit,
    onDeleteCancelled: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onMissingAcknowledged: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Deleted from another device. Nothing in this dialog can be saved to
    // a row that is gone, so the only thing offered is the way out.
    val gone = editor.error == CategoryEditorError.Missing

    AlertDialog(
        onDismissRequest = { if (!editor.working) onDismiss() },
        title = {
            Text(
                text = when {
                    editor.category == null -> stringResource(R.string.categories_new_title)
                    else -> stringResource(R.string.categories_edit_title)
                },
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                when {
                    gone -> Unit

                    editor.confirmingDelete -> {
                        // Names the category: "are you sure?" over a list
                        // asks a question the owner cannot check.
                        Text(
                            text = stringResource(
                                R.string.categories_delete_confirm,
                                editor.category?.name.orEmpty(),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.categories_delete_note),
                            style = MaterialTheme.snTextStyles.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> {
                        OutlinedTextField(
                            value = editor.name,
                            onValueChange = onNameChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.categories_name_label)) },
                            singleLine = true,
                            enabled = !editor.working,
                            isError = editor.error != null,
                        )

                        if (editor.category != null) {
                            // Written the moment it is flipped, not on
                            // Save: it is one column of its own, and the
                            // effect is stated under it because "hidden"
                            // taking the pieces with it is not something
                            // anyone would guess.
                            VisibilityToggle(
                                visible = editor.category.isVisible,
                                enabled = !editor.working,
                                onChange = onVisibilityChange,
                            )

                            TextButton(
                                onClick = onDeleteRequested,
                                enabled = !editor.working,
                                modifier = Modifier.heightIn(min = Tokens.Layout.touchTarget),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            ) {
                                Text(stringResource(R.string.categories_delete))
                            }
                        }
                    }
                }

                editor.error?.let { error ->
                    Text(
                        text = error.message(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )

                    // A refusal with nothing to do about it is a dead
                    // end. Refiling the pieces is the only way to a
                    // deletable category, and this is the way to them.
                    if (error is CategoryEditorError.InUse) {
                        TextButton(
                            onClick = onShowPieces,
                            modifier = Modifier.heightIn(min = Tokens.Layout.touchTarget),
                        ) {
                            Text(stringResource(R.string.categories_show_pieces))
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                gone -> Button(onClick = onMissingAcknowledged) {
                    Text(stringResource(R.string.categories_missing_refresh))
                }

                editor.confirmingDelete -> TextButton(
                    onClick = onDeleteConfirmed,
                    enabled = !editor.working,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.categories_delete_button))
                }

                else -> TextButton(onClick = onSave, enabled = !editor.working) {
                    Text(
                        text = when (editor.category) {
                            null -> stringResource(R.string.categories_add_button)
                            else -> stringResource(R.string.categories_save)
                        },
                    )
                }
            }
        },
        dismissButton = {
            if (!gone) {
                TextButton(
                    onClick = if (editor.confirmingDelete) onDeleteCancelled else onDismiss,
                    enabled = !editor.working,
                ) {
                    Text(stringResource(R.string.categories_cancel))
                }
            }
        },
    )
}

/**
 * Shown or hidden, and what that does to a customer.
 *
 * The same shape as the piece-level toggles in `ProductActionsSheet`, and
 * for the same reason: the place a shop owner will actually read what a
 * switch means is under the switch.
 */
@Composable
private fun VisibilityToggle(visible: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.Layout.touchTarget),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.categories_visible),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (visible) {
                    stringResource(R.string.categories_visible_effect)
                } else {
                    stringResource(R.string.categories_hidden_effect)
                },
                style = MaterialTheme.snTextStyles.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = visible, onCheckedChange = onChange, enabled = enabled)
    }
}

/** Each failure says what the owner can do about it, or that nothing will. */
@Composable
private fun CategoryEditorError.message(): String = when (this) {
    is CategoryEditorError.NameBlank -> stringResource(R.string.categories_error_name_blank)
    is CategoryEditorError.NameTaken -> stringResource(R.string.categories_error_name_taken)
    is CategoryEditorError.SlugExhausted ->
        stringResource(R.string.categories_error_name_exhausted)

    is CategoryEditorError.Missing -> stringResource(R.string.categories_missing)
    // The count is what makes this actionable rather than a bare no. It
    // is absent only when the count itself could not be read, and the
    // refusal is still correct then.
    is CategoryEditorError.InUse -> pieces?.let {
        pluralStringResource(R.plurals.categories_error_in_use_count, it, it)
    } ?: stringResource(R.string.categories_error_in_use)
    is CategoryEditorError.Failed -> if (failure.offline) {
        stringResource(R.string.categories_error_offline)
    } else {
        stringResource(
            R.string.categories_error_server,
            failure.detail ?: stringResource(R.string.categories_no_detail),
        )
    }
}

/**
 * No categories at all — a statement with the one next step, not an
 * error. ux.md rules 1 and 3.
 */
@Composable
private fun EmptyCategories(onAdd: () -> Unit) = Column(
    modifier = Modifier.fillMaxSize().padding(Tokens.Space.s4),
    verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
) {
    Text(
        text = stringResource(R.string.categories_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = onAdd, modifier = Modifier.heightIn(min = Tokens.Layout.touchTarget)) {
        Text(stringResource(R.string.categories_add_first))
    }
}

@Composable
private fun CategoriesError(failure: RequestFailure, onRetry: () -> Unit) = Column(
    modifier = Modifier.fillMaxSize().padding(Tokens.Space.s4),
    verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
) {
    Text(
        text = if (failure.offline) {
            stringResource(R.string.categories_offline)
        } else {
            stringResource(
                R.string.categories_error,
                failure.detail ?: stringResource(R.string.categories_no_detail),
            )
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
    TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = Tokens.Layout.touchTarget)) {
        Text(stringResource(R.string.categories_retry))
    }
}

/** Skeleton rows at the real rows' height, so nothing moves on arrival. */
@Composable
private fun CategoriesSkeleton() = Column(
    modifier = Modifier.fillMaxWidth().padding(vertical = Tokens.Space.s2),
) {
    repeat(SKELETON_ROWS) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ROW_HEIGHT)
                .padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s2),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(SKELETON_BAR_FRACTION)
                    .height(SKELETON_BAR_HEIGHT)
                    .clip(RoundedCornerShape(Tokens.Radius.sm))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private const val SKELETON_ROWS = 6
private const val SKELETON_BAR_FRACTION = 0.5f

private val ROW_HEIGHT = 56.dp
private val SKELETON_BAR_HEIGHT = 12.dp

@PreviewLightDark
@Composable
private fun CategoriesPreview() {
    SnTheme {
        CategoriesScreen(
            uiState = CategoriesUiState(
                loading = false,
                categories = listOf(
                    Category("1", "Bridal Jewellery", isVisible = true, displayOrder = 1),
                    Category("2", "Necklaces", isVisible = true, displayOrder = 2),
                    Category("3", "Unreleased Collection", isVisible = false, displayOrder = 3),
                ),
            ),
        )
    }
}

@PreviewLightDark
@Composable
private fun CategoriesEmptyPreview() {
    SnTheme { CategoriesScreen(uiState = CategoriesUiState(loading = false)) }
}

@PreviewLightDark
@Composable
private fun CategoryDialogPreview() {
    SnTheme {
        CategoriesScreen(
            uiState = CategoriesUiState(
                loading = false,
                categories = listOf(Category("1", "Necklaces", isVisible = true, displayOrder = 1)),
                editor = CategoryEditor(
                    category = Category("1", "Necklaces", isVisible = true, displayOrder = 1),
                    name = "Necklaces",
                    error = CategoryEditorError.InUse(pieces = 12),
                ),
            ),
        )
    }
}
