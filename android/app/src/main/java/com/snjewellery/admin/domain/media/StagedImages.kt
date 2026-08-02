package com.snjewellery.admin.domain.media

/**
 * The photographs of a piece, held on the device between choosing them
 * and uploading them.
 *
 * Both ways in end here, and both end up as a file this app owns:
 *
 * - **The camera** cannot hand back an image. It writes into a location
 *   given to it and reports whether it succeeded, so [newCaptureTarget]
 *   decides that location.
 * - **The gallery** hands back a URI belonging to the photo picker, and
 *   the read access that comes with it is temporary — it does not
 *   outlive the app's process. Rendering that URI after the app has been
 *   reclaimed, or uploading it in M7.7, would fail on something nothing
 *   is allowed to open any more. So [copyIn] takes a copy rather than
 *   keeping the reference.
 *
 * The result is that everything downstream — the thumbnails, M7.5's
 * ordering, M7.6's compression, M7.7's upload — deals with one kind of
 * thing, and none of them has to ask where a photograph came from.
 *
 * No Android imports, and URIs as `String` rather than `android.net.Uri`,
 * so `domain` stays testable on the JVM — the rule
 * [ProductFormRules][com.snjewellery.admin.domain.product.ProductFormRules]
 * follows. The string is also what a `SavedStateHandle` can hold, which
 * is what makes a photograph survive the app being reclaimed.
 */
interface StagedImages {

    /**
     * A URI a camera app may write **one** full-size photograph into.
     *
     * `null` when no target could be made — in practice, a phone with no
     * room left on it. The caller must say so rather than opening a
     * camera that will fail on the shutter.
     */
    fun newCaptureTarget(): String?

    /**
     * Copies the photograph at [sourceUri] into this app's own storage,
     * and returns the URI of the copy.
     *
     * `null` when it could not be copied — no room, or a source that
     * cannot be read. One failure says nothing about the next, so a
     * caller adding several has to decide about each of them.
     */
    suspend fun copyIn(sourceUri: String): String?
}
