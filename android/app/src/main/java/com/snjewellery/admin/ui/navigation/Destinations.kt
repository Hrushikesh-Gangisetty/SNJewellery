package com.snjewellery.admin.ui.navigation

import kotlinx.serialization.Serializable

/**
 * The destinations inside the authenticated app.
 *
 * Type-safe routes: a destination is a `@Serializable` object, not a
 * string. Navigation Compose builds the route from the type, so a
 * misspelled destination is a compile error rather than a crash on a
 * screen the owner reaches once a week — the same reason ADR-0007 chose
 * Hilt over runtime-resolved injection.
 *
 * **Login is deliberately not here.** Whether the app shows sign-in is
 * decided by the session, not by navigation — see `RootViewModel`. If it
 * were a destination, two things would own the answer and a signed-out
 * user could be left on a screen the back stack still held.
 *
 * Nothing is declared here before the screen exists. M8 adds the
 * catalogue list and Edit Product.
 */
@Serializable
data object Dashboard

/** The Add Product form (M7.1). */
@Serializable
data object AddProduct

/** The owner's whole catalogue, newest first (M8.1). */
@Serializable
data object Catalogue

/**
 * The confirmation after a piece is saved (M7.12).
 *
 * A `data class` rather than an object, so the piece it is confirming
 * travels in the route and survives process death with the back stack.
 * Holding it in a view model instead would show "Saved" with no name
 * after the app was reclaimed on the confirmation screen.
 */
@Serializable
data class ProductSaved(val name: String, val slug: String)
