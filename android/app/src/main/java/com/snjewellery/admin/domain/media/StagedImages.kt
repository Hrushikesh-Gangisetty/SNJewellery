package com.snjewellery.admin.domain.media

/**
 * The photographs of a piece, held on the device between choosing them
 * and uploading them.
 *
 * Both ways in end at [stage], and both come out of it as one kind of
 * thing: a compressed WebP file this app owns.
 *
 * - **The camera** cannot hand back an image. It writes into a location
 *   given to it and reports whether it succeeded, so [newCaptureTarget]
 *   decides that location — and what it writes there is a raw
 *   full-resolution photograph that must be staged before it is of any
 *   use, then thrown away.
 * - **The gallery** hands back a URI belonging to the photo picker, and
 *   the read access that comes with it is temporary — it does not
 *   outlive the app's process. Rendering that URI after the app has been
 *   reclaimed, or uploading it in M7.7, would fail on something nothing
 *   is allowed to open any more.
 *
 * The result is that everything downstream — the thumbnails, M7.5's
 * ordering, M7.7's upload — deals with one kind of thing, and none of
 * them has to ask where a photograph came from or how large it was.
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
     * Reads the photograph at [sourceUri], compresses and resizes it to
     * the target ADR-0005 sets, and returns the URI of the result.
     *
     * The source is left alone — it may not be ours to delete, and when
     * it is (a camera capture) the caller discards it once this has
     * succeeded.
     *
     * `null` when it could not be staged: no room, a source that cannot
     * be read, or an image too large to decode. One failure says nothing
     * about the next, so a caller adding several has to decide about each
     * of them.
     */
    suspend fun stage(sourceUri: String): String?

    /**
     * Throws away a staged photograph the owner has removed.
     *
     * Nothing depends on it having worked — the photograph is already off
     * the form either way, and the cache is reclaimable. It exists so a
     * long session of adding and rejecting shots does not leave every
     * rejected one on the phone.
     */
    suspend fun discard(uri: String)
}
