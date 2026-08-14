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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snjewellery.admin.R
import com.snjewellery.admin.domain.RequestFailure
import com.snjewellery.admin.domain.dashboard.DashboardMetrics
import com.snjewellery.admin.domain.dashboard.RecentProduct
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

                    // In the body rather than a third top-bar action: the
                    // bar already holds two, and a row of three short
                    // words is where a top bar stops being readable.
                    OutlinedButton(
                        onClick = onViewCatalogue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Tokens.Layout.touchTarget),
                    ) {
                        Text(stringResource(R.string.dashboard_view_catalogue))
                    }
                }
            }
        }
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
