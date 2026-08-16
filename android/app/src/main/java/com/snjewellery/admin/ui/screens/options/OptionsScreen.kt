package com.snjewellery.admin.ui.screens.options

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snjewellery.admin.R
import com.snjewellery.admin.domain.RequestFailure
import com.snjewellery.admin.domain.rates.Metal
import com.snjewellery.admin.domain.rates.MetalRate
import com.snjewellery.admin.domain.rates.RateProblem
import com.snjewellery.admin.ui.theme.SnTheme
import com.snjewellery.admin.ui.theme.Tokens
import com.snjewellery.admin.ui.theme.snTextStyles
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The owner's options. Today's gold and silver rates, which the website
 * shows in place of per-piece purity and weight.
 *
 * ── Each metal saves on its own ──────────────────────────────────────
 * Two independent columns, so two independent writes. One button for both
 * would make a partial failure ambiguous — the owner would not know which
 * number the catalogue ended up with — and each row can then show its own
 * "last set", which is the thing they actually check each morning.
 *
 * ── The panel needs both ─────────────────────────────────────────────
 * The website renders nothing until gold and silver are both published,
 * so one rate on its own achieves nothing a customer can see. The screen
 * says so rather than leaving that to be discovered.
 */
@Composable
fun OptionsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OptionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    OptionsScreen(
        uiState = uiState,
        onBack = onBack,
        onRetry = viewModel::load,
        onRateChange = viewModel::onRateChange,
        onSave = viewModel::onSave,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OptionsScreen(
    uiState: OptionsUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {},
    onRateChange: (Metal, String) -> Unit = { _, _ -> },
    onSave: (Metal) -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.options_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(
                            text = stringResource(R.string.options_back),
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
            Text(
                text = stringResource(R.string.options_rates_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.options_rates_body),
                style = MaterialTheme.snTextStyles.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when {
                uiState.loading -> Text(
                    text = stringResource(R.string.options_rates_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                uiState.failure != null -> RatesError(uiState.failure, onRetry)

                else -> {
                    uiState.rows.forEach { row ->
                        RateField(
                            row = row,
                            onChange = { onRateChange(row.rate.metal, it) },
                            onSave = { onSave(row.rate.metal) },
                        )
                    }

                    // Not an error: one rate set and the other not is a
                    // normal half-finished morning. It is worth saying
                    // because the consequence is invisible — the website
                    // shows nothing at all until both are published.
                    if (uiState.halfPublished) {
                        Text(
                            text = stringResource(R.string.options_rates_half),
                            style = MaterialTheme.snTextStyles.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RateField(row: RateRow, onChange: (String) -> Unit, onSave: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
        ) {
            OutlinedTextField(
                value = row.typed,
                onValueChange = onChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(row.rate.metal.labelRes())) },
                suffix = { Text(stringResource(R.string.options_rate_per_gram)) },
                singleLine = true,
                enabled = !row.saving,
                isError = row.problem != null,
                // Decimal, because a silver rate is not a whole number of
                // rupees. The gold one usually is, and typing "9240" on a
                // decimal keypad costs nothing.
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                supportingText = { Text(row.status()) },
            )

            row.problem?.let { problem ->
                Text(
                    text = stringResource(problem.messageRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            row.failure?.let { failure ->
                Text(
                    text = if (failure.offline) {
                        stringResource(R.string.options_rate_offline)
                    } else {
                        stringResource(
                            R.string.options_rate_error,
                            failure.detail ?: stringResource(R.string.options_no_detail),
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onSave,
                    // Nothing to save when the field already matches what
                    // the catalogue has — a button that writes the value
                    // that is already there teaches the owner it does
                    // nothing.
                    enabled = !row.saving && row.changed,
                    modifier = Modifier.heightIn(min = Tokens.Layout.touchTarget),
                ) {
                    Text(
                        text = if (row.saving) {
                            stringResource(R.string.options_rate_saving)
                        } else {
                            stringResource(R.string.options_rate_save)
                        },
                    )
                }

                if (row.justSaved && !row.changed) {
                    Text(
                        text = stringResource(R.string.options_rate_saved),
                        style = MaterialTheme.snTextStyles.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * What the catalogue currently has for this metal — which is not always
 * what is in the field above it, and that difference is the whole reason
 * this line exists.
 */
@Composable
private fun RateRow.status(): String = when {
    !rate.published -> stringResource(R.string.options_rate_unpublished)
    else -> stringResource(R.string.options_rate_set_at, rate.setAt.asSetTime())
}

@Composable
private fun RatesError(failure: RequestFailure, onRetry: () -> Unit) = Column(
    verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
) {
    Text(
        text = if (failure.offline) {
            stringResource(R.string.options_rates_offline)
        } else {
            stringResource(
                R.string.options_rates_error,
                failure.detail ?: stringResource(R.string.options_no_detail),
            )
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
    TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = Tokens.Layout.touchTarget)) {
        Text(stringResource(R.string.options_retry))
    }
}

private fun Metal.labelRes(): Int = when (this) {
    Metal.Gold -> R.string.options_rate_gold
    Metal.Silver -> R.string.options_rate_silver
}

private fun RateProblem.messageRes(): Int = when (this) {
    RateProblem.NotANumber -> R.string.options_rate_not_a_number
    RateProblem.NotPositive -> R.string.options_rate_not_positive
}

/**
 * `2026-08-16T09:12:00Z` → `16 Aug, 09:12`.
 *
 * The **time** is shown as well as the date, unlike the dashboard's
 * recent-uploads list: a rate is a daily figure, so "was this set this
 * morning or last night?" is exactly the question being asked. An
 * unparseable value renders as itself rather than throwing.
 */
@Composable
private fun String?.asSetTime(): String {
    if (this == null) return stringResource(R.string.options_rate_never)

    return try {
        DateTimeFormatter
            .ofPattern(SET_AT_PATTERN, Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            .format(Instant.parse(this))
    } catch (e: Exception) {
        this
    }
}

private const val SET_AT_PATTERN = "d MMM, HH:mm"

@PreviewLightDark
@Composable
private fun OptionsPreview() {
    SnTheme {
        OptionsScreen(
            uiState = OptionsUiState(
                loading = false,
                rows = listOf(
                    RateRow(
                        rate = MetalRate(Metal.Gold, 9240.0, "2026-08-16T04:12:00Z"),
                        typed = "9240",
                    ),
                    RateRow(
                        rate = MetalRate(Metal.Silver, null, null),
                        typed = "",
                    ),
                ),
            ),
        )
    }
}

@PreviewLightDark
@Composable
private fun OptionsOfflinePreview() {
    SnTheme {
        OptionsScreen(
            uiState = OptionsUiState(
                loading = false,
                failure = RequestFailure(offline = true),
            ),
        )
    }
}
