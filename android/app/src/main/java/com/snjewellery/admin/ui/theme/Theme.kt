package com.snjewellery.admin.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * The application theme. Every screen sits inside one of these, and no
 * screen may declare a colour, size, radius or duration of its own —
 * ADR-0008 makes this the only route from the design tokens to the UI.
 *
 * ── Dynamic colour is overridden ─────────────────────────────────────
 * Material 3 can recolour an app from the user's wallpaper. **This app
 * does not**, resolving the open sub-question ADR-0008 left to M6.2.
 *
 * The reason is that this palette is not a preference, it is the brand.
 * The gold in colour.md is the shop's gold, the same value the website
 * serves, and ADR-0008 exists precisely so those two cannot drift.
 * Wallpaper-derived colour would make the admin app disagree with the
 * website on every device, differently on each one — and would do it to
 * an app whose whole subject is how gold looks. It would also silently
 * void the contrast validation the token generator runs: those 32 pairs
 * are checked against these values, not against whatever the wallpaper
 * produces.
 *
 * Dark mode is still honoured, because that is a legibility preference
 * rather than a brand one — the shop owner photographing stock in a dim
 * back room is the case it exists for.
 */

/** The steps Material's own scale has no slot for. See [SnTextStyles]. */
internal val LocalSnTextStyles = staticCompositionLocalOf { SnTextStyles }

@Composable
fun SnTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) SnDarkColorScheme else SnLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // The status bar icons have to invert with the scheme, or
            // dark icons land on the dark surface and disappear. The
            // window background itself comes from themes.xml, which
            // covers only the frames before this composes.
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalSnTextStyles provides SnTextStyles) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SnTypography,
            shapes = SnShapes,
            content = content,
        )
    }
}

/**
 * Reaches the two type steps Material has no slot for, so a screen never
 * needs to touch [Tokens] directly.
 *
 * `MaterialTheme.snTextStyles.displayXl` reads the same way as
 * `MaterialTheme.typography.bodyLarge`, which is the point.
 */
internal val MaterialTheme.snTextStyles: SnTextStyles
    @Composable get() = LocalSnTextStyles.current
