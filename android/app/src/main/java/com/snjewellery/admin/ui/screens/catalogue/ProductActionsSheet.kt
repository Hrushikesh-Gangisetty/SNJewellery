package com.snjewellery.admin.ui.screens.catalogue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.snjewellery.admin.R
import com.snjewellery.admin.domain.catalogue.CatalogueEntry
import com.snjewellery.admin.domain.product.ProductStatus
import com.snjewellery.admin.ui.theme.SnTheme
import com.snjewellery.admin.ui.theme.Tokens
import com.snjewellery.admin.ui.theme.snTextStyles

/**
 * What the owner can do to one piece: the PRD's three status actions, and
 * delete.
 *
 * ── Why a sheet rather than a row of icons ───────────────────────────
 * Five controls per row would make the list unreadable and unhittable —
 * M1.8's 48 dp target does not fit five times across a 375 dp phone
 * beside a thumbnail. A tap opens the actions for the piece that was
 * tapped, which also gives each one room to say what it *means* rather
 * than relying on an icon the owner has to learn.
 *
 * ── Each toggle states its effect on the website ─────────────────────
 * M8.5 asks for the visibility rules to be documented, and the place a
 * shop owner will actually read them is under the switch. Sold and
 * Archived are the pair that would otherwise be confused, and the
 * difference is the whole reason the PRD names both.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProductActionsSheet(
    entry: CatalogueEntry,
    action: ActionState?,
    onToggle: (ProductStatus) -> Unit,
    onDeleteRequested: () -> Unit,
    onDeleteCancelled: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val working = action is ActionState.Working

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Space.s4)
                .padding(bottom = Tokens.Space.s6),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.categoryName,
                style = MaterialTheme.snTextStyles.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            ProductStatus.entries.forEach { status ->
                StatusToggle(
                    status = status,
                    checked = entry.has(status),
                    enabled = !working,
                    onToggle = { onToggle(status) },
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            when (action) {
                // Delete is the one action with no undo, so it is the one
                // action that asks. The confirmation names the piece,
                // because a dialog that says "are you sure?" over a list
                // is answering a question the owner cannot check.
                is ActionState.Confirming -> DeleteConfirmation(
                    name = entry.name,
                    onCancel = onDeleteCancelled,
                    onConfirm = onDeleteConfirmed,
                )

                else -> TextButton(
                    onClick = onDeleteRequested,
                    enabled = !working,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Tokens.Layout.touchTarget),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.actions_delete))
                }
            }

            if (action is ActionState.Vanished) {
                Text(
                    text = stringResource(R.string.actions_vanished),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.Layout.touchTarget),
                ) {
                    Text(stringResource(R.string.actions_vanished_refresh))
                }
            }

            if (action is ActionState.Failed) {
                Text(
                    text = if (action.failure.offline) {
                        stringResource(R.string.actions_offline)
                    } else {
                        stringResource(
                            R.string.actions_error,
                            action.failure.detail
                                ?: stringResource(R.string.catalogue_no_detail),
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun StatusToggle(
    status: ProductStatus,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Tokens.Layout.touchTarget),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(status.titleRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(status.effectRes()),
                style = MaterialTheme.snTextStyles.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
    }
}

@Composable
private fun DeleteConfirmation(name: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        Text(
            text = stringResource(R.string.actions_delete_confirm, name),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // Archive is offered here rather than only as a switch above,
        // because this is the moment the owner learns the two are
        // different — and archiving is what most of them actually want.
        Text(
            text = stringResource(R.string.actions_delete_or_archive),
            style = MaterialTheme.snTextStyles.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).heightIn(min = Tokens.Layout.touchTarget),
            ) {
                Text(stringResource(R.string.actions_cancel))
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f).heightIn(min = Tokens.Layout.touchTarget),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(R.string.actions_delete_confirm_button))
            }
        }
    }
}

private fun ProductStatus.titleRes(): Int = when (this) {
    ProductStatus.Featured -> R.string.catalogue_badge_featured
    ProductStatus.Sold -> R.string.catalogue_badge_sold
    ProductStatus.Archived -> R.string.catalogue_badge_archived
}

/** What the flag does to what a customer sees. M8.5's documented rules. */
private fun ProductStatus.effectRes(): Int = when (this) {
    ProductStatus.Featured -> R.string.actions_featured_effect
    ProductStatus.Sold -> R.string.actions_sold_effect
    ProductStatus.Archived -> R.string.actions_archived_effect
}

@PreviewLightDark
@Composable
private fun DeleteConfirmationPreview() {
    SnTheme {
        Column(modifier = Modifier.padding(Tokens.Space.s4)) {
            DeleteConfirmation(name = "Kundan Choker Set", onCancel = {}, onConfirm = {})
        }
    }
}
