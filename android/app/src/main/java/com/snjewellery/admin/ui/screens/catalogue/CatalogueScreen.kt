package com.snjewellery.admin.ui.screens.catalogue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.snjewellery.admin.domain.product.ProductStatus
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import com.snjewellery.admin.domain.catalogue.CatalogueQuery
import com.snjewellery.admin.domain.catalogue.Category
import com.snjewellery.admin.domain.catalogue.StatusFilter
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.snjewellery.admin.R
import com.snjewellery.admin.domain.RequestFailure
import com.snjewellery.admin.domain.catalogue.CatalogueEntry
import com.snjewellery.admin.ui.theme.SnTheme
import com.snjewellery.admin.ui.theme.Tokens
import com.snjewellery.admin.ui.theme.snTextStyles
import kotlinx.coroutines.flow.filter

/**
 * The whole catalogue, newest first — the screen the owner opens to find a
 * piece rather than to count them.
 *
 * ── Paged by cursor, one page ahead ──────────────────────────────────
 * The next page is requested when the last few rows come into view rather
 * than when the very last one does, so on a normal scroll the rows are
 * already there. See `CatalogueListRepository` for why the cursor is a
 * row and not an offset.
 *
 * ── Archived pieces appear here ──────────────────────────────────────
 * Badged, not hidden. The dashboard's counts exclude them because they
 * answer "how big is my catalogue"; this list answers "what have I got",
 * and archiving is reversible — a piece missing from the website *and*
 * from the app would be unrecoverable. M8.2 adds the filter.
 */
@Composable
fun CatalogueScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CatalogueViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // A piece saved, edited or deleted on the Add Product screen changes
    // this list, and coming back to a stale one is how the owner ends up
    // entering the same piece twice.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    CatalogueScreen(
        uiState = uiState,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::refresh,
        onRetryMore = viewModel::retryMore,
        onRefresh = viewModel::pullToRefresh,
        onSearchChange = viewModel::onSearchChange,
        onStatusChange = viewModel::onStatusChange,
        onCategoryChange = viewModel::onCategoryChange,
        onClearFilters = viewModel::onClearFilters,
        onSelect = viewModel::onSelect,
        onToggle = viewModel::onToggle,
        onDeleteRequested = viewModel::onDeleteRequested,
        onDeleteCancelled = viewModel::onDeleteCancelled,
        onDeleteConfirmed = viewModel::onDeleteConfirmed,
        onDismissSheet = viewModel::onDismissSheet,
        onOrphanNoticeDismissed = viewModel::onOrphanNoticeDismissed,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CatalogueScreen(
    uiState: CatalogueUiState,
    modifier: Modifier = Modifier,
    onLoadMore: () -> Unit = {},
    onRetry: () -> Unit = {},
    onRetryMore: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onSearchChange: (String) -> Unit = {},
    onStatusChange: (StatusFilter) -> Unit = {},
    onCategoryChange: (String?) -> Unit = {},
    onClearFilters: () -> Unit = {},
    onSelect: (CatalogueEntry) -> Unit = {},
    onToggle: (ProductStatus) -> Unit = {},
    onDeleteRequested: () -> Unit = {},
    onDeleteCancelled: () -> Unit = {},
    onDeleteConfirmed: () -> Unit = {},
    onDismissSheet: () -> Unit = {},
    onOrphanNoticeDismissed: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.catalogue_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(
                            text = stringResource(R.string.catalogue_back),
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
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // Above the list and always present, including while it loads
            // and when nothing matched. A filter row that disappears with
            // its results is a filter the owner cannot undo.
            CatalogueFilters(
                query = uiState.query,
                categories = uiState.categories,
                onSearchChange = onSearchChange,
                onStatusChange = onStatusChange,
                onCategoryChange = onCategoryChange,
            )

            PullToRefreshBox(
                // `loading` is the first-page state, which already draws
                // skeletons. Binding the spinner to it too would show the
                // owner two loading indicators for one request.
                isRefreshing = uiState.refreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    uiState.loading -> CatalogueSkeleton()

                    uiState.failure != null -> CatalogueError(
                        failure = uiState.failure,
                        onRetry = onRetry,
                    )

                    // Nothing matched and nothing exists are different
                    // messages with different next steps.
                    uiState.isNoMatch -> NoMatches(onClearFilters)

                    uiState.isEmpty -> EmptyCatalogue()

                    else -> CatalogueList(
                        uiState = uiState,
                        onLoadMore = onLoadMore,
                        onRetryMore = onRetryMore,
                        onSelect = onSelect,
                    )
                }
            }
        }
    }

    uiState.selected?.let { entry ->
        ProductActionsSheet(
            entry = entry,
            action = uiState.action,
            onToggle = onToggle,
            onDeleteRequested = onDeleteRequested,
            onDeleteCancelled = onDeleteCancelled,
            onDeleteConfirmed = onDeleteConfirmed,
            onRefresh = onRetry,
            onDismiss = onDismissSheet,
        )
    }

    // The piece is gone; only its photographs were left behind. A dialog
    // rather than an inline line, because the sheet it would have sat in
    // has already closed.
    if (uiState.orphanedImages) {
        AlertDialog(
            onDismissRequest = onOrphanNoticeDismissed,
            title = { Text(stringResource(R.string.actions_orphaned_title)) },
            text = { Text(stringResource(R.string.actions_orphaned_body)) },
            confirmButton = {
                TextButton(onClick = onOrphanNoticeDismissed) {
                    Text(stringResource(R.string.actions_orphaned_dismiss))
                }
            },
        )
    }
}

/**
 * Search, status and category.
 *
 * ── Status is one choice, not three switches ──────────────────────────
 * Featured, Sold and Archived are independent properties of a piece, so
 * three checkboxes would let the owner ask for combinations that mean
 * nothing and would need explaining on a screen used one-handed over a
 * counter. What they actually want is one of a short list of questions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogueFilters(
    query: CatalogueQuery,
    categories: List<Category>,
    onSearchChange: (String) -> Unit,
    onStatusChange: (StatusFilter) -> Unit,
    onCategoryChange: (String?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Space.s4)
            .padding(bottom = Tokens.Space.s2),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
    ) {
        OutlinedTextField(
            value = query.text,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.catalogue_search)) },
            supportingText = { Text(stringResource(R.string.catalogue_search_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            trailingIcon = if (query.text.isEmpty()) {
                null
            } else {
                {
                    TextButton(onClick = { onSearchChange("") }) {
                        Text(
                            text = stringResource(R.string.catalogue_search_clear),
                            style = MaterialTheme.snTextStyles.label,
                        )
                    }
                }
            },
        )

        // Scrolls rather than wrapping: five chips will not fit across a
        // 375 dp phone, and a wrapped row changes the list's start position
        // as the labels change length.
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
        ) {
            StatusFilter.entries.forEach { status ->
                FilterChip(
                    selected = query.status == status,
                    onClick = { onStatusChange(status) },
                    label = { Text(stringResource(status.labelRes())) },
                )
            }
        }

        // Hidden until the categories arrive: an empty dropdown is a
        // control that looks broken. Its failure is silent for the same
        // reason — it costs the filter, not the list.
        if (categories.isNotEmpty()) {
            CategoryFilter(
                categories = categories,
                selectedId = query.categoryId,
                onSelect = onCategoryChange,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilter(
    categories: List<Category>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val allLabel = stringResource(R.string.catalogue_category_all)
    val selected = categories.firstOrNull { it.id == selectedId }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.name ?: allLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.catalogue_category)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // "Every category" has to be reachable once one is chosen, or
            // the filter is a one-way door.
            DropdownMenuItem(
                text = { Text(allLabel) },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        onSelect(category.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun StatusFilter.labelRes(): Int = when (this) {
    StatusFilter.Live -> R.string.catalogue_filter_live
    StatusFilter.All -> R.string.catalogue_filter_all
    StatusFilter.Featured -> R.string.catalogue_badge_featured
    StatusFilter.Sold -> R.string.catalogue_badge_sold
    StatusFilter.Archived -> R.string.catalogue_badge_archived
}

/**
 * Nothing matched — with the one action that fixes it.
 *
 * Worded apart from an empty catalogue, because the next step is the
 * opposite: clear the filters, not add a piece. ux.md rule 1 and rule 3.
 */
@Composable
private fun NoMatches(onClearFilters: () -> Unit) = Column(
    modifier = Modifier.fillMaxSize().padding(Tokens.Space.s4),
    verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
) {
    Text(
        text = stringResource(R.string.catalogue_no_matches),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(
        onClick = onClearFilters,
        modifier = Modifier.heightIn(min = Tokens.Layout.touchTarget),
    ) {
        Text(stringResource(R.string.catalogue_clear_filters))
    }
}

@Composable
private fun CatalogueList(
    uiState: CatalogueUiState,
    onLoadMore: () -> Unit,
    onRetryMore: () -> Unit,
    onSelect: (CatalogueEntry) -> Unit,
) {
    val listState = rememberLazyListState()

    // Derived from the scroll position rather than from an `onClick` on the
    // last row: a row can be composed and never looked at, and a fling can
    // pass the trigger row without it ever settling there.
    LaunchedEffect(listState, uiState.entries.size, uiState.hasMore) {
        if (!uiState.hasMore) return@LaunchedEffect

        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .filter { it >= uiState.entries.size - PREFETCH_ROWS }
            .collect { onLoadMore() }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = Tokens.Space.s2),
    ) {
        // Keyed by id, so recomposition and scroll position survive a page
        // arriving — and a refresh that reorders the list moves rows rather
        // than redrawing every one of them.
        items(uiState.entries, key = { it.id }) { entry ->
            CatalogueRow(entry, onClick = { onSelect(entry) })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        if (uiState.loadingMore) {
            item(key = LOADING_MORE_KEY) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(Tokens.Space.s4),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(Tokens.Space.s6))
                }
            }
        }

        // A page failing does not replace the screenful the owner already
        // has. The error sits where the missing rows would be.
        uiState.moreFailure?.let { failure ->
            item(key = MORE_FAILURE_KEY) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(Tokens.Space.s4),
                    verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
                ) {
                    Text(
                        text = if (failure.offline) {
                            stringResource(R.string.catalogue_more_offline)
                        } else {
                            stringResource(R.string.catalogue_more_error)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(
                        onClick = onRetryMore,
                        modifier = Modifier.heightIn(min = Tokens.Layout.touchTarget),
                    ) {
                        Text(stringResource(R.string.catalogue_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogueRow(entry: CatalogueEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = ROW_HEIGHT)
            .padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s2),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumbnail(entry)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1),
        ) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.categoryName,
                style = MaterialTheme.snTextStyles.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StatusBadges(entry)
        }
    }
}

/**
 * The primary image, or a placeholder shaped exactly like it.
 *
 * The box is a fixed size whether or not there is a photograph, so a piece
 * with none does not shrink its row and a slow image does not push the
 * text sideways as it lands. Same reason the website fixes its aspect
 * ratios (M2.9).
 */
@Composable
private fun Thumbnail(entry: CatalogueEntry) {
    val shape = RoundedCornerShape(Tokens.Radius.sm)

    if (entry.thumbnailUrl == null) {
        Box(
            modifier = Modifier
                .size(THUMBNAIL_SIZE)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.catalogue_no_photo),
                style = MaterialTheme.snTextStyles.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        AsyncImage(
            model = entry.thumbnailUrl,
            // The piece's name, not "photograph of…": a screen reader
            // moving down this list is reading an index, and the name is
            // the only part that distinguishes one row from the next.
            contentDescription = entry.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(THUMBNAIL_SIZE).clip(shape),
        )
    }
}

/**
 * Featured, Sold and Archived — shown only when true.
 *
 * All three can hold at once, which is why they are three badges rather
 * than one status word: a featured piece that has sold is a real state,
 * and collapsing it would lose whichever the app decided mattered less.
 */
@Composable
private fun StatusBadges(entry: CatalogueEntry) {
    val badges = buildList {
        if (entry.featured) add(R.string.catalogue_badge_featured)
        if (entry.sold) add(R.string.catalogue_badge_sold)
        if (entry.archived) add(R.string.catalogue_badge_archived)
    }

    if (badges.isEmpty()) return

    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        badges.forEach { label ->
            Text(
                text = stringResource(label),
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
    }
}

/**
 * Skeleton rows at the real rows' dimensions, so nothing moves when the
 * pieces arrive — the pattern the dashboard established (ux.md rule 3).
 */
@Composable
private fun CatalogueSkeleton() {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Tokens.Space.s2)) {
        repeat(SKELETON_ROWS) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ROW_HEIGHT)
                    .padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s2),
                horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(THUMBNAIL_SIZE)
                        .clip(RoundedCornerShape(Tokens.Radius.sm))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
                ) {
                    SkeletonBar(widthFraction = 0.7f)
                    SkeletonBar(widthFraction = 0.4f)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun SkeletonBar(widthFraction: Float) = Box(
    modifier = Modifier
        .fillMaxWidth(widthFraction)
        .height(SKELETON_BAR_HEIGHT)
        .clip(RoundedCornerShape(Tokens.Radius.sm))
        .background(MaterialTheme.colorScheme.surfaceVariant),
)

@Composable
private fun EmptyCatalogue() = Column(
    modifier = Modifier.fillMaxSize().padding(Tokens.Space.s4),
    verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
) {
    Text(
        text = stringResource(R.string.catalogue_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CatalogueError(failure: RequestFailure, onRetry: () -> Unit) = Column(
    modifier = Modifier.fillMaxSize().padding(Tokens.Space.s4),
    verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
) {
    Text(
        text = if (failure.offline) {
            stringResource(R.string.catalogue_offline)
        } else {
            stringResource(
                R.string.catalogue_error,
                failure.detail ?: stringResource(R.string.catalogue_no_detail),
            )
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
    TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = Tokens.Layout.touchTarget)) {
        Text(stringResource(R.string.catalogue_retry))
    }
}

/** How many rows from the bottom to start fetching the next page. */
private const val PREFETCH_ROWS = 5

private const val SKELETON_ROWS = 6
private const val LOADING_MORE_KEY = "loading_more"
private const val MORE_FAILURE_KEY = "more_failure"

private val THUMBNAIL_SIZE = 64.dp
private val ROW_HEIGHT = 80.dp
private val SKELETON_BAR_HEIGHT = 12.dp

@PreviewLightDark
@Composable
private fun CataloguePreview() {
    SnTheme {
        CatalogueScreen(
            uiState = CatalogueUiState(
                loading = false,
                entries = listOf(
                    CatalogueEntry(
                        id = "1",
                        name = "Kundan Choker Set",
                        slug = "kundan-choker-set",
                        categoryName = "Bridal Jewellery",
                        thumbnailUrl = null,
                        featured = true,
                        sold = false,
                        archived = false,
                        addedAt = "2026-08-14T10:00:00Z",
                    ),
                    CatalogueEntry(
                        id = "2",
                        name = "Antique Temple Necklace",
                        slug = "antique-temple-necklace",
                        categoryName = "Necklaces",
                        thumbnailUrl = null,
                        featured = false,
                        sold = true,
                        archived = true,
                        addedAt = "2026-08-13T10:00:00Z",
                    ),
                ),
            ),
        )
    }
}

@PreviewLightDark
@Composable
private fun CatalogueLoadingPreview() {
    SnTheme { CatalogueScreen(uiState = CatalogueUiState(loading = true)) }
}

@PreviewLightDark
@Composable
private fun CatalogueEmptyPreview() {
    SnTheme { CatalogueScreen(uiState = CatalogueUiState(loading = false)) }
}
