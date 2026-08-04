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
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
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
 * [rememberPhotoSources], which the stateful screen builds. That split
 * is what keeps `@Preview` working: an activity result launcher cannot be
 * created without an `ActivityResultRegistryOwner`, and a preview has
 * none, so a launcher anywhere on this path would take the previews down
 * with it.
 *
 * The list **is** the order, and the first entry is the primary image.
 * There is no separate `display_order` held anywhere for the two to
 * disagree about, which is what makes M7.5's requirement — that what is
 * on screen is what gets persisted — true by construction rather than by
 * keeping something in step.
 */
@Composable
internal fun ProductImages(
    images: List<String>,
    photoProblem: PhotoProblem?,
    addingPhotos: Boolean,
    modifier: Modifier = Modifier,
    uploadProgress: Map<String, Float> = emptyMap(),
    onTakePhoto: () -> Unit = {},
    onChoosePhotos: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onMoveEarlier: (Int) -> Unit = {},
    onMoveLater: (Int) -> Unit = {},
    onRemove: (Int) -> Unit = {},
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
            // A plain Column, not a LazyColumn: this sits inside a
            // vertically scrolling form, where a nested lazy list has no
            // height to measure against. A piece has a handful of
            // photographs, so there is nothing to virtualise anyway.
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                images.forEachIndexed { index, uri ->
                    key(uri) {
                        ProductImageRow(
                            uri = uri,
                            index = index,
                            total = images.size,
                            progress = uploadProgress[uri],
                            // Reordering or removing a photograph while
                            // its bytes are going up would change an
                            // order that is already being written.
                            editable = uploadProgress.isEmpty(),
                            onMoveEarlier = { onMoveEarlier(index) },
                            onMoveLater = { onMoveLater(index) },
                            onRemove = { onRemove(index) },
                        )
                    }
                }
            }
        }

        // Side by side and equally weighted. Neither is the fallback: a
        // piece photographed at the counter comes from the camera, and one
        // already shot properly comes from the gallery, and the shop does
        // both.
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            OutlinedButton(
                onClick = onTakePhoto,
                enabled = !addingPhotos,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Tokens.Layout.touchTarget),
            ) {
                Text(stringResource(R.string.add_product_take_photo))
            }
            OutlinedButton(
                onClick = onChoosePhotos,
                // The one place in this form a control is disabled, and
                // only while its own work is in flight — pressing it again
                // mid-copy would add the same selection twice. The Save
                // button's rule still holds: nothing is greyed out for
                // being incomplete.
                enabled = !addingPhotos,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Tokens.Layout.touchTarget),
            ) {
                Text(stringResource(R.string.add_product_choose_photos))
            }
        }

        if (addingPhotos) {
            // Full-size photographs are megabytes each, and a button that
            // appears to do nothing for two seconds gets pressed again.
            Text(
                text = stringResource(R.string.add_product_images_adding),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (photoProblem != null) {
            PhotoProblemMessage(problem = photoProblem, onOpenSettings = onOpenSettings)
        }
    }
}

/**
 * One photograph, with everything that can be done to it.
 *
 * ── Why arrows rather than dragging ──────────────────────────────────
 * Compose has no reorderable list, so dragging would mean a third-party
 * library or a hand-written gesture — weight and a maintenance burden for
 * a list of three or four items (CLAUDE.md §3.7). Arrows also need no
 * discovering, work one-handed on a phone held over a counter, and are
 * the only version of this a screen reader can operate at all.
 *
 * The arrows at the ends are disabled. That is not the Save button's rule
 * being broken — Save is never disabled because the reason would be
 * invisible, whereas the reason the first photograph cannot move up is
 * that it is the first photograph, which is on the screen.
 */
@Composable
private fun ProductImageRow(
    uri: String,
    index: Int,
    total: Int,
    progress: Float?,
    editable: Boolean,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onRemove: () -> Unit,
) {
    val position = index + 1

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = uri,
            // The photograph itself carries no description anyone could
            // write, so what is announced is where it sits — which is
            // exactly what the buttons beside it change.
            contentDescription = stringResource(
                R.string.add_product_image_position,
                position,
                total,
            ),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(THUMBNAIL_SIZE)
                .clip(MaterialTheme.shapes.small),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1),
        ) {
            Text(
                text = if (index == 0) {
                    stringResource(R.string.add_product_image_main)
                } else {
                    stringResource(R.string.add_product_image_number, position)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (index == 0) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            // Per image, not just overall — M7.7's requirement, and the
            // reason it is worth the room: on mobile data the owner needs
            // to see that *this* photograph is moving, not that some
            // aggregate is.
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        IconButton(onClick = onMoveEarlier, enabled = editable && index > 0) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = stringResource(
                    R.string.add_product_image_move_earlier,
                    position,
                ),
            )
        }
        IconButton(onClick = onMoveLater, enabled = editable && index < total - 1) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(
                    R.string.add_product_image_move_later,
                    position,
                ),
            )
        }
        IconButton(onClick = onRemove, enabled = editable) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.add_product_image_remove, position),
                tint = MaterialTheme.colorScheme.error,
            )
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
private fun PhotoProblemMessage(problem: PhotoProblem, onOpenSettings: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        Text(
            text = when (problem) {
                PhotoProblem.CameraRefused -> stringResource(R.string.add_product_camera_refused)
                PhotoProblem.CameraBlocked -> stringResource(R.string.add_product_camera_blocked)
                PhotoProblem.NoCameraApp -> stringResource(R.string.add_product_camera_none)
                PhotoProblem.NoGalleryApp -> stringResource(R.string.add_product_gallery_none)
                PhotoProblem.NoStorage -> stringResource(R.string.add_product_photo_no_storage)
                is PhotoProblem.SomeNotAdded -> pluralStringResource(
                    R.plurals.add_product_photos_not_added,
                    problem.count,
                    problem.count,
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )

        // Only where it leads somewhere. Settings cannot restore a camera
        // the phone does not have, or make room on a full one.
        if (problem == PhotoProblem.CameraBlocked) {
            TextButton(
                onClick = onOpenSettings,
                modifier = Modifier.heightIn(min = Tokens.Layout.touchTarget),
            ) {
                Text(stringResource(R.string.add_product_camera_settings))
            }
        }
    }
}

/** Everything the two ways in need from the framework, in one place. */
internal data class PhotoSources(
    val takePhoto: () -> Unit,
    val choosePhotos: () -> Unit,
    val openSettings: () -> Unit,
)

/**
 * Wires the camera and the photo picker to the view model.
 *
 * ── The gallery asks for no permission at all ────────────────────────
 * `PickVisualMedia` needs no storage permission on any version, and is
 * backported below the platform picker, so there is one code path from
 * `minSdk` 26 upward and nothing to deny. That is the decision
 * docs/architecture/android-build.md §2 made when it set the floor at 26
 * — the granular media permissions were avoided rather than branched on.
 *
 * The URIs it returns are read-only and **temporary**, which is why the
 * view model copies rather than keeps them. See [StagedImages].
 *
 * ── Why the camera permission is checked before it is requested ──────
 * `RequestPermission` returns immediately with the existing answer when
 * one is already held, so requesting unconditionally would work — but it
 * costs a round trip through the activity on every single photograph, and
 * the owner is taking several in a row. Checking first is the fast path.
 *
 * ── Why the refusal is read from the activity ────────────────────────
 * `granted = false` alone cannot tell "not this time" from "stop asking",
 * and those need different words. `shouldShowRequestPermissionRationale`
 * read *after* the refusal is the only thing that distinguishes them.
 */
@Composable
internal fun rememberPhotoSources(
    onNewCaptureTarget: () -> String?,
    onCaptured: (Boolean) -> Unit,
    onChosen: (List<String>) -> Unit,
    onCameraRefused: (canAskAgain: Boolean) -> Unit,
    onCameraUnavailable: () -> Unit,
    onGalleryUnavailable: () -> Unit,
): PhotoSources {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
        onCaptured,
    )

    // The no-argument contract takes the system's own maximum rather than
    // a number invented here. A shop owner photographing one piece will
    // never approach it, and capping it lower would be a limit with no
    // reason behind it.
    val pickPhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris -> onChosen(uris.map(Uri::toString)) }

    fun capture() {
        val target = onNewCaptureTarget() ?: return
        try {
            takePicture.launch(Uri.parse(target))
        } catch (_: ActivityNotFoundException) {
            // A phone with no camera app at all. Rare, and the manifest
            // allows it to install, so it must not be a crash.
            onCameraUnavailable()
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
            onCameraRefused(
                activity != null && ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.CAMERA,
                ),
            )
        }
    }

    return PhotoSources(
        takePhoto = {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED

            if (granted) capture() else requestCamera.launch(Manifest.permission.CAMERA)
        },
        choosePhotos = {
            try {
                // Images only. This catalogue has no use for a video, and
                // offering one would let the owner pick something that
                // fails later rather than now.
                pickPhotos.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            } catch (_: ActivityNotFoundException) {
                onGalleryUnavailable()
            }
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
        ProductImages(images = emptyList(), photoProblem = null, addingPhotos = false)
    }
}

@PreviewLightDark
@Composable
private fun ProductImagesBlockedPreview() {
    SnTheme {
        ProductImages(
            images = emptyList(),
            photoProblem = PhotoProblem.CameraBlocked,
            addingPhotos = false,
        )
    }
}

@PreviewLightDark
@Composable
private fun ProductImagesAddingPreview() {
    SnTheme {
        ProductImages(images = emptyList(), photoProblem = null, addingPhotos = true)
    }
}

/**
 * The URIs resolve to nothing in a preview, so the thumbnails render
 * empty — what this is for is the row: the main-image label, and which
 * arrows are disabled at which end.
 */
@PreviewLightDark
@Composable
private fun ProductImagesOrderedPreview() {
    SnTheme {
        ProductImages(
            images = listOf("content://preview/1", "content://preview/2", "content://preview/3"),
            photoProblem = null,
            addingPhotos = false,
        )
    }
}
