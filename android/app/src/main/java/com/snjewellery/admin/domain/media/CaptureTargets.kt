package com.snjewellery.admin.domain.media

/**
 * Somewhere for a camera app to put a photograph.
 *
 * The capture intent does not hand back an image; it writes into a
 * location given to it and reports whether it succeeded. This is the seam
 * that decides that location, so the view model can start a capture
 * without knowing that a file, a provider or an authority is involved.
 *
 * No Android imports, and a URI as a `String` rather than an
 * `android.net.Uri`, so `domain` stays testable on the JVM — the rule
 * [ProductFormRules][com.snjewellery.admin.domain.product.ProductFormRules]
 * follows. The string is also what a `SavedStateHandle` can hold, which
 * matters because a capture has to survive the app being reclaimed while
 * the camera is open.
 */
interface CaptureTargets {

    /**
     * A URI a camera app may write **one** full-size photograph into.
     *
     * `null` when no target could be made — in practice, a phone with no
     * room left on it. The caller must say so rather than opening a
     * camera that will fail on the shutter.
     */
    fun newTarget(): String?
}
