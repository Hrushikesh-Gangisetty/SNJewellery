package com.snjewellery.admin.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snjewellery.admin.R
import com.snjewellery.admin.domain.RequestFailure
import com.snjewellery.admin.domain.dashboard.DashboardMetrics
import com.snjewellery.admin.domain.dashboard.RecentProduct
import com.snjewellery.admin.domain.draft.PendingDraft
import com.snjewellery.admin.domain.draft.SyncState
import com.snjewellery.admin.domain.product.ProductDraft
import com.snjewellery.admin.ui.theme.SnTheme
import com.snjewellery.admin.ui.theme.Tokens
import com.snjewellery.admin.ui.theme.snTextStyles
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The app's start destination once signed in: the four figures the PRD's
 * Dashboard section names, live from Supabase.
 *
 * Carries the top bar, which is where **Sign out** lives (M6.10).
 *
 * ── The three states are genuinely different ─────────────────────────
 * ux.md rule 3. **Loading** is skeleton tiles at the real tiles'
 * dimensions, so no number arrives by pushing the layout down.
 * **Failed** is an inline error where the numbers belong, with Retry and
 * with "no connection" worded apart from a server fault. **Empty** —
 * nothing uploaded yet — is a plain statement, not an error, because
 * having no products is a normal state for a shop that has just
 * installed the app.
 */
@Composable
fun DashboardScreen(
    onSignOut: () -> Unit,
    onAddProduct: () -> Unit,
    onViewCatalogue: () -> Unit,
    onManageCategories: () -> Unit,
    onOpenOptions: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // On resume, not in the view model's `init`: coming back from Add
    // Product must show a total that includes what was just saved, and
    // this view model outlives that navigation.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }

    DashboardScreen(
        uiState = uiState,
        onSignOut = onSignOut,
        onAddProduct = onAddProduct,
        onViewCatalogue = onViewCatalogue,
        onManageCategories = onManageCategories,
        onOpenOptions = onOpenOptions,
        onSyncNow = viewModel::syncNow,
        onRetry = viewModel::load,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardScreen(
    uiState: DashboardUiState,
    modifier: Modifier = Modifier,
    onSignOut: () -> Unit = {},
    onAddProduct: () -> Unit = {},
    onViewCatalogue: () -> Unit = {},
    onManageCategories: () -> Unit = {},
    onOpenOptions: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    TextButton(onClick = onAddProduct) {
                        Text(
                            text = stringResource(R.string.dashboard_add_product),
                            style = MaterialTheme.snTextStyles.label,
                        )
                    }
                    TextButton(onClick = onSignOut) {
                        Text(
                            text = stringResource(R.string.dashboard_sign_out),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
        ) {
            if (!uiState.configured) {
                // `error`, not the accent: this build cannot reach the
                // backend at all, and it must not look like status.
                Text(
                    text = stringResource(
                        R.string.dashboard_backend_missing,
                        uiState.missingConfig.joinToString(", "),
                    ),
                    style = MaterialTheme.snTextStyles.label,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // Above the metrics, and outside the `when`: a piece waiting
            // on this phone is the one thing on this screen the owner has
            // to act on, and it is most likely to exist exactly when the
            // counts below it cannot be loaded.
            if (uiState.pending.isNotEmpty()) {
                PendingUploads(
                    pending = uiState.pending,
                    sync = uiState.sync,
                    onSyncNow = onSyncNow,
                )
            }

            when (val state = uiState.state) {
                is DashboardState.Loading -> MetricsSkeleton()

                is DashboardState.Failed -> MetricsError(
                    failure = state.failure,
                    onRetry = onRetry,
                )

                is DashboardState.Loaded -> if (state.metrics.isEmpty) {
                    EmptyCatalogue(onAddProduct = onAddProduct)
                } else {
                    Metrics(metrics = state.metrics)
                }
            }

            // In the body rather than more top-bar actions: the bar
            // already holds two, and a row of four short words is where a
            // top bar stops being readable.
            //
            // Outside the `when`, so both survive an empty catalogue and a
            // failed load. Setting the categories up is what an owner does
            // BEFORE the first upload — exactly when there are no figures
            // to draw — and neither screen needs the metrics to work.
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                OutlinedButton(
                    onClick = onViewCatalogue,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = Tokens.Layout.touchTarget),
                ) {
                    Text(stringResource(R.string.dashboard_view_catalogue))
                }
                OutlinedButton(
                    onClick = onManageCategories,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = Tokens.Layout.touchTarget),
                ) {
                    Text(stringResource(R.string.dashboard_categories))
                }
                OutlinedButton(
                    onClick = onOpenOptions,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = Tokens.Layout.touchTarget),
                ) {
                    Text(stringResource(R.string.dashboard_options))
                }
            }
        }
    }
}

/**
 * Pieces entered on this phone that are not in the catalogue yet.
 *
 * ── Why this is not an error ─────────────────────────────────────────
 * Nothing has gone wrong that the owner did. They photographed a ring
 * somewhere with no signal, which is the case the whole feature exists
 * for. So it states what is waiting and why, in the ordinary text colour
 * — an error-red panel every time the shop's signal drops would teach
 * them to ignore it.
 *
 * Each row says why that one is still waiting, because "no connection"
 * and "the server refused it" call for different things: one resolves
 * itself, the other will not.
 */
@Composable
private fun PendingUploads(
    pending: List<PendingDraft>,
    sync: SyncState,
    onSyncNow: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
        ) {
            Text(
                text = pluralStringResource(
                    R.plurals.dashboard_pending_title,
                    pending.size,
                    pending.size,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.dashboard_pending_body),
                style = MaterialTheme.snTextStyles.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            pending.forEach { draft ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Tokens.Layout.touchTarget)
                        .padding(vertical = Tokens.Space.s1),
                    verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1),
                ) {
                    Text(
                        text = draft.draft.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = draft.waitingReason(sync),
                        style = MaterialTheme.snTextStyles.label,
                        color = if (draft.productId == sync.nameUnavailable) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            // Offered as well as the automatic pass: "wait for a
            // reconnection" is not something anyone can act on when the
            // signal is present and something else went wrong. Inert
            // while a pass is running, so a second tap cannot start one
            // over the top of it.
            TextButton(
                onClick = onSyncNow,
                enabled = sync.sending == null,
                modifier = Modifier.heightIn(min = Tokens.Layout.touchTarget),
            ) {
                Text(
                    text = if (sync.sending == null) {
                        stringResource(R.string.dashboard_pending_try_now)
                    } else {
                        stringResource(R.string.dashboard_pending_sending_now)
                    },
                )
            }
        }
    }
}

/** Photographs waiting, and why this one has not gone up. */
@Composable
private fun PendingDraft.waitingReason(sync: SyncState): String {
    val photos = pluralStringResource(
        R.plurals.dashboard_pending_photos,
        photoUris.size,
        photoUris.size,
    )

    return when {
        productId == sync.sending -> stringResource(R.string.dashboard_pending_sending, photos)

        // Named separately because it is the one failure that will not
        // clear itself: the sync has stopped retrying it, and only a
        // different name will help.
        productId == sync.nameUnavailable ->
            stringResource(R.string.dashboard_pending_name_taken, photos)

        failure == null -> photos
        failure.offline -> stringResource(R.string.dashboard_pending_offline, photos)
        else -> stringResource(R.string.dashboard_pending_failed, photos)
    }
}

@Composable
private fun Metrics(metrics: DashboardMetrics) {
    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        MetricTile(
            label = stringResource(R.string.dashboard_total_products),
            value = metrics.totalProducts.toString(),
            modifier = Modifier.weight(1f),
        )
        MetricTile(
            label = stringResource(R.string.dashboard_new_uploads),
            value = metrics.newUploads.toString(),
            note = stringResource(R.string.dashboard_new_uploads_window),
            modifier = Modifier.weight(1f),
        )
        MetricTile(
            label = stringResource(R.string.dashboard_featured),
            value = metrics.featuredProducts.toString(),
            modifier = Modifier.weight(1f),
        )
    }

    Text(
        text = stringResource(R.string.dashboard_recently_added),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )

    Column {
        metrics.recentlyAdded.forEachIndexed { index, product ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            RecentRow(product)
        }
    }
}

/**
 * `OutlinedCard`, not a hand-rolled border: layout.md §3 makes
 * "elevation 0 with a hairline border" the default treatment, which is
 * exactly what an outlined card is. The design system names *hairline*
 * but defines no width token, and inventing a `1.dp` here would be the
 * hard-coded value ADR-0008 forbids. Its colours resolve through the
 * scheme to `border` and `surface`.
 *
 * A fixed height, so [MetricsSkeleton] can match it exactly rather than
 * approximately — the tiles sit in a `Row` and would otherwise take
 * their height from whichever label wraps.
 */
@Composable
private fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    note: String? = null,
) {
    OutlinedCard(modifier = modifier.height(TILE_HEIGHT)) {
        Column(
            modifier = Modifier.padding(Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1),
        ) {
            // `spec` is the tabular-figures step — a row of counts that
            // do not line up reads as carelessness on a screen of
            // numbers.
            Text(
                text = value,
                style = MaterialTheme.snTextStyles.spec,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.snTextStyles.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (note != null) {
                Text(
                    text = note,
                    style = MaterialTheme.snTextStyles.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecentRow(product: RecentProduct) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Tokens.Layout.touchTarget)
            .padding(vertical = Tokens.Space.s2),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = product.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = product.addedAt.asShortDate(),
            style = MaterialTheme.snTextStyles.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Skeleton at the real tiles' dimensions. ux.md: "a skeleton of the
 * wrong size causes the exact layout shift it exists to prevent", so the
 * height here is the tile's content height, not a guess.
 */
@Composable
private fun MetricsSkeleton() {
    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        repeat(METRIC_TILE_COUNT) {
            SkeletonBlock(
                modifier = Modifier
                    .weight(1f)
                    .height(TILE_HEIGHT),
            )
        }
    }
    repeat(SKELETON_ROWS) {
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .height(Tokens.Layout.touchTarget),
        )
    }
}

@Composable
private fun SkeletonBlock(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(Tokens.Radius.lg),
            ),
    ) {}
}

@Composable
private fun MetricsError(failure: RequestFailure, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
    ) {
        Text(
            text = if (failure.offline) {
                stringResource(R.string.dashboard_error_offline)
            } else {
                stringResource(
                    R.string.dashboard_error_server,
                    failure.detail ?: stringResource(R.string.dashboard_error_no_detail),
                )
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.heightIn(min = Tokens.Layout.touchTarget),
        ) {
            Text(stringResource(R.string.dashboard_retry))
        }
    }
}

/**
 * Nothing uploaded yet. A statement, not an error.
 *
 * ux.md rule 1 asks every empty state for a next step. M6.11 shipped this
 * without one because the step it wanted — "Add your first product" — had
 * nowhere to go, and a button that does nothing is worse than a missing
 * one. M7 built the destination, so the action arrives here now.
 */
@Composable
private fun EmptyCatalogue(onAddProduct: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.dashboard_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onAddProduct,
            modifier = Modifier.heightIn(min = Tokens.Layout.touchTarget),
        ) {
            Text(stringResource(R.string.dashboard_add_first_product))
        }
    }
}

/**
 * `2026-07-29T12:31:02.31Z` → `29 Jul`. The year is omitted deliberately:
 * this list is the last five uploads, where the day is the useful part.
 *
 * An unparseable value renders as itself rather than throwing — a
 * malformed timestamp must not take down the whole dashboard.
 */
@Composable
private fun String.asShortDate(): String = try {
    DateTimeFormatter
        .ofPattern(SHORT_DATE_PATTERN, Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(this))
} catch (e: Exception) {
    this
}

private const val SHORT_DATE_PATTERN = "d MMM"
private const val METRIC_TILE_COUNT = 3
private const val SKELETON_ROWS = 3
private val TILE_HEIGHT = Tokens.Space.s24

@PreviewLightDark
@Composable
private fun DashboardLoadedPreview() {
    SnTheme {
        DashboardScreen(
            uiState = DashboardUiState(
                state = DashboardState.Loaded(
                    DashboardMetrics(
                        totalProducts = 11,
                        newUploads = 4,
                        featuredProducts = 3,
                        recentlyAdded = listOf(
                            RecentProduct("1", "Antique Temple Necklace", "2026-07-29T09:12:00Z"),
                            RecentProduct("2", "Ruby Drop Earrings", "2026-07-28T16:40:00Z"),
                        ),
                    ),
                ),
            ),
        )
    }
}

@PreviewLightDark
@Composable
private fun DashboardLoadingPreview() {
    SnTheme { DashboardScreen(uiState = DashboardUiState(state = DashboardState.Loading)) }
}

@PreviewLightDark
@Composable
private fun DashboardOfflinePreview() {
    SnTheme {
        DashboardScreen(
            uiState = DashboardUiState(
                state = DashboardState.Failed(RequestFailure(offline = true)),
            ),
        )
    }
}

@PreviewLightDark
@Composable
private fun DashboardEmptyPreview() {
    SnTheme {
        DashboardScreen(
            uiState = DashboardUiState(
                state = DashboardState.Loaded(
                    DashboardMetrics(0, 0, 0, emptyList()),
                ),
            ),
        )
    }
}
