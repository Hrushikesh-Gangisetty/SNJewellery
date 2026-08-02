package com.snjewellery.admin.ui.screens.addproduct

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.snjewellery.admin.R
import com.snjewellery.admin.ui.theme.SnTheme
import com.snjewellery.admin.ui.theme.Tokens
import com.snjewellery.admin.ui.theme.snTextStyles
import androidx.compose.ui.tooling.preview.PreviewLightDark

/**
 * The photographs section of the Add Product form.
 *
 * Presentation only — every framework interaction is hoisted into
 * [rememberCameraCapture], which the stateful screen builds. That split
 * is what keeps `@Preview` working: an activity result launcher cannot be
 * created without an `ActivityResultRegistryOwner`, and a preview has
 * none, so a launcher anywhere on this path would take the previews down
 * with it.
 *
 * M7.3 shows what has been captured. Reordering, removal, and the primary
 * badge are M7.5; the gallery is M7.4.
 */
@Composable
internal fun ProductImages(
    images: List<String>,
    cameraProblem: CameraProblem?,
    modifier: Modifier = Modifier,
    onTakePhoto: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
    ) {
        Text(
            text = stringResource(R.string.add_product_images),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (images.isEmpty()) {
            // ux.md rule 3: nothing here yet is not something broken. It
            // also says what the order will mean, before there is an
            // order to see.
            Text(
                text = stringResource(R.string.add_product_images_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                itemsIndexed(images, key = { _, uri -> uri }) { index, uri ->
                    AsyncImage(
                        model = uri,
                        // The photograph itself carries no description
                        // anyone could write, so the position is what is
                        // announced — which is also the thing M7.5 lets
                        // the owner change.
                        contentDescription = stringResource(
                            R.string.add_product_image_position,
                            index + 1,
                            images.size,
                        ),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(THUMBNAIL_SIZE)
                            .clip(MaterialTheme.shapes.small),
                    )
                }
            }
        }

        OutlinedButton(
            onClick = onTakePhoto,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Tokens.Layout.touchTarget),
        ) {
            Text(stringResource(R.string.add_product_take_photo))
        }

        if (cameraProblem != null) {
            CameraProblemMessage(problem = cameraProblem, onOpenSettings = onOpenSettings)
        }
    }
}

/**
 * What went wrong, and the way out of it.
 *
 * Every case names an action, because a refused permission with nothing
 * offered after it is a dead button — the failure M7.3 exists to prevent.
 */
@Composable
private fun CameraProblemMessage(problem: CameraProblem, onOpenSettings: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        Text(
            text = stringResource(
                when (problem) {
                    CameraProblem.PermissionRefused -> R.string.add_product_camera_refused
                    CameraProblem.PermissionBlocked -> R.string.add_product_camera_blocked
                    CameraProblem.NoCameraApp -> R.string.add_product_camera_none
                    CameraProblem.NoStorage -> R.string.add_product_camera_no_storage
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )

        // Only where it leads somewhere. Settings cannot restore a camera
        // the phone does not have, or make room on a full one.
        if (problem == CameraProblem.PermissionBlocked) {
            TextButton(
                onClick = onOpenSettings,
                modifier = Modifier.heightIn(min = Tokens.Layout.touchTarget),
            ) {
                Text(stringResource(R.string.add_product_camera_settings))
            }
        }
    }
}

/** Everything the capture flow needs from the framework, in one place. */
internal data class CameraCapture(
    val takePhoto: () -> Unit,
    val openSettings: () -> Unit,
)

/**
 * Wires the permission request and the capture intent to the view model.
 *
 * ── Why the permission is checked before it is requested ──────────────
 * `RequestPermission` returns immediately with the existing answer when
 * one is already held, so requesting unconditionally would work — but it
 * costs a round trip through the activity on every single photograph, and
 * the owner is taking several in a row. Checking first is the fast path.
 *
 * ── Why the refusal is read from the activity ─────────────────────────
 * `granted = false` alone cannot tell "not this time" from "stop asking",
 * and those need different words. `shouldShowRequestPermissionRationale`
 * read *after* the refusal is the only thing that distinguishes them.
 */
@Composable
internal fun rememberCameraCapture(
    onNewTarget: () -> String?,
    onCaptured: (Boolean) -> Unit,
    onRefused: (canAskAgain: Boolean) -> Unit,
    onUnavailable: () -> Unit,
): CameraCapture {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
        onCaptured,
    )

    fun capture() {
        val target = onNewTarget() ?: return
        try {
            takePicture.launch(Uri.parse(target))
        } catch (_: ActivityNotFoundException) {
            // A phone with no camera app at all. Rare, and the manifest
            // allows it to install, so it must not be a crash.
            onUnavailable()
        }
    }

    val requestCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            capture()
        } else {
            // No activity means no way to ask again either, so the
            // blocked wording is the honest one.
            onRefused(
                activity != null && ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.CAMERA,
                ),
            )
        }
    }

    return CameraCapture(
        takePhoto = {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED

            if (granted) capture() else requestCamera.launch(Manifest.permission.CAMERA)
        },
        openSettings = {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
    )
}

/**
 * `LocalContext` is not reliably the activity — Compose wraps it, and a
 * themed wrapper is what arrives here on some devices.
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Square, and the same square as the website's grid thumbnails.
 *
 * `Space.s24` is 96dp from the spacing scale rather than a size token of
 * its own: layout.md builds sizes from that scale, and inventing a
 * `thumbnail` value here would be a design decision made in Compose
 * instead of in docs/design (ADR-0008).
 */
private val THUMBNAIL_SIZE = Tokens.Space.s24

@PreviewLightDark
@Composable
private fun ProductImagesEmptyPreview() {
    SnTheme {
        ProductImages(images = emptyList(), cameraProblem = null)
    }
}

@PreviewLightDark
@Composable
private fun ProductImagesBlockedPreview() {
    SnTheme {
        ProductImages(images = emptyList(), cameraProblem = CameraProblem.PermissionBlocked)
    }
}
