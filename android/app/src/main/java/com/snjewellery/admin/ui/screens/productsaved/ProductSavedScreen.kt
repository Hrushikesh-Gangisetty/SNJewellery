package com.snjewellery.admin.ui.screens.productsaved

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.snjewellery.admin.R
import com.snjewellery.admin.ui.theme.SnTheme
import com.snjewellery.admin.ui.theme.Tokens

/**
 * The piece is saved. What now?
 *
 * ── Why this is a destination and not a message on the form ───────────
 * It is not only tidier — it is what stops a second copy of the piece.
 * The form's Save button remains live after a successful save, and the
 * attempt record is cleared, so pressing it again would insert a *new*
 * product. Navigating here and popping Add Product off the back stack
 * means there is no route back into a form holding a piece that has
 * already been entered. Back from here goes to the dashboard.
 *
 * ── Why "Add another" is the primary action ───────────────────────────
 * The PRD's owner photographs jewellery between customers, so the
 * question after a save is almost never "and now let me go and look at
 * it" — it is "next piece". A fresh form arrives with an empty
 * `SavedStateHandle` because re-entering the destination is a new back
 * stack entry, so nothing of the last piece is carried over.
 *
 * ── Why View may not be there ────────────────────────────────────────
 * It appears only when this build was told where the website is. Until
 * M5 puts the site on a domain there is nothing for it to open, and a
 * button that lands on a 404 on the shop's own site would read as the
 * piece not having saved. Same rule the website follows for social links
 * it has not been given (M4.10). **Edit arrives with M8.3**, which is the
 * other half of this task's "view or edit".
 */
@Composable
fun ProductSavedScreen(
    name: String,
    slug: String,
    onAddAnother: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProductSavedViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var noBrowser by remember { mutableStateOf(false) }
    val url = viewModel.websiteUrl(slug)

    ProductSavedScreen(
        name = name,
        websiteUrl = url,
        noBrowser = noBrowser,
        onView = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, url?.toUri()))
            } catch (_: ActivityNotFoundException) {
                // A phone with no browser is unlikely and not impossible,
                // and a tap that does nothing at all would read as the
                // piece being unreachable.
                noBrowser = true
            }
        },
        onAddAnother = onAddAnother,
        onDone = onDone,
        modifier = modifier,
    )
}

@Composable
internal fun ProductSavedScreen(
    name: String,
    modifier: Modifier = Modifier,
    websiteUrl: String? = null,
    noBrowser: Boolean = false,
    onView: () -> Unit = {},
    onAddAnother: () -> Unit = {},
    onDone: () -> Unit = {},
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
        ) {
            Text(
                text = stringResource(R.string.product_saved_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // The piece is named rather than merely counted. "Saved" on its
            // own is the sort of message someone stops reading, and after
            // a morning of uploads the owner needs to know *which* one.
            Text(
                text = stringResource(R.string.product_saved_body, name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = onAddAnother,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Tokens.Layout.touchTarget),
            ) {
                Text(stringResource(R.string.product_saved_add_another))
            }

            if (websiteUrl != null) {
                OutlinedButton(
                    onClick = onView,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Tokens.Layout.touchTarget),
                ) {
                    Text(stringResource(R.string.product_saved_view))
                }
            }

            if (noBrowser) {
                Text(
                    text = stringResource(R.string.product_saved_no_browser),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            TextButton(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Tokens.Layout.touchTarget),
            ) {
                Text(stringResource(R.string.product_saved_done))
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun ProductSavedPreview() {
    SnTheme {
        ProductSavedScreen(
            name = "Kundan Choker Set",
            websiteUrl = "https://example.com/product/kundan-choker-set",
        )
    }
}

@PreviewLightDark
@Composable
private fun ProductSavedNoWebsitePreview() {
    SnTheme { ProductSavedScreen(name = "Kundan Choker Set") }
}
