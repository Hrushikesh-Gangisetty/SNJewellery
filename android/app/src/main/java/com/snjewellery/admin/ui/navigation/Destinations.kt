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
 * One destination today, honestly. M7 adds Add Product and M8 the
 * catalogue list; nothing is declared here before the screen exists.
 */
@Serializable
data object Dashboard
