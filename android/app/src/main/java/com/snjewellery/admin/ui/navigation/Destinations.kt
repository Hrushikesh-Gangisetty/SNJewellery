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

/**
 * The same form as [AddProduct], filled from an existing piece (M8.3).
 *
 * The property name is the argument key the view model reads off its
 * `SavedStateHandle` — see `AddProductViewModel.KEY_PRODUCT_ID`. Renaming
 * it here without renaming that is a form that silently opens blank.
 */
@Serializable
data class EditProduct(val productId: String)

/**
 * The owner's whole catalogue, newest first (M8.1).
 *
 * [categoryId] opens it already filtered — the way out of M8.8's refusal
 * to delete a category with pieces in it, which is otherwise a dead end
 * with nothing to act on. Null is the ordinary "show me everything" entry
 * from the dashboard.
 *
 * The property name is the argument key `CatalogueViewModel` reads off
 * its `SavedStateHandle`. Renaming it here without renaming that is a
 * screen that silently opens unfiltered.
 */
@Serializable
data class Catalogue(val categoryId: String? = null)

/** The categories every piece is filed under (M8.6). */
@Serializable
data object Categories

/** The owner's options — today's metal rates (M8.12). */
@Serializable
data object Options

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
