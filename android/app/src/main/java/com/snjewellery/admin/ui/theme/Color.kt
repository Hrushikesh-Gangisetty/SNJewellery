package com.snjewellery.admin.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

/**
 * Design tokens mapped into Material 3 colour schemes.
 *
 * See docs/design/colour.md for the palette and its contrast rules; the
 * generator validates 32 contrast pairs on every run (ADR-0008), so a
 * scheme built from these values inherits that guarantee.
 *
 * ── The mapping is not one-to-one, on purpose ────────────────────────
 * Material has roles this palette has no opinion about — `tertiary`,
 * `inversePrimary`, four container tones per role. Rather than invent
 * colours to fill them, each is pointed at the nearest token the design
 * system does define. A screen that reaches for an unmapped role gets a
 * brand colour, never a Material default that came from nowhere.
 *
 * `secondary` and `tertiary` both resolve to the accent deliberately.
 * brand.md gives this identity **one** accent — gold — and inventing a
 * second so Material's slots look full would put a colour on screen that
 * the design system never approved.
 */

internal val SnLightColorScheme: ColorScheme = lightColorScheme(
    primary = Tokens.Light.accent,
    onPrimary = Tokens.Light.onAccent,
    primaryContainer = Tokens.Light.surfaceRaised,
    onPrimaryContainer = Tokens.Light.accentText,

    secondary = Tokens.Light.accent,
    onSecondary = Tokens.Light.onAccent,
    secondaryContainer = Tokens.Light.surfaceSunken,
    onSecondaryContainer = Tokens.Light.textPrimary,

    tertiary = Tokens.Light.accent,
    onTertiary = Tokens.Light.onAccent,
    tertiaryContainer = Tokens.Light.surfaceSunken,
    onTertiaryContainer = Tokens.Light.textPrimary,

    background = Tokens.Light.surface,
    onBackground = Tokens.Light.textPrimary,

    surface = Tokens.Light.surface,
    onSurface = Tokens.Light.textPrimary,
    // Material uses `onSurfaceVariant` for secondary text and for icons
    // in most of its components, so it takes `text-secondary` rather
    // than a border tone — the usual mis-mapping, and it produces grey
    // labels that fail contrast.
    surfaceVariant = Tokens.Light.surfaceSunken,
    onSurfaceVariant = Tokens.Light.textSecondary,

    surfaceContainerLowest = Tokens.Light.surface,
    surfaceContainerLow = Tokens.Light.surfaceRaised,
    surfaceContainer = Tokens.Light.surfaceRaised,
    surfaceContainerHigh = Tokens.Light.surfaceSunken,
    surfaceContainerHighest = Tokens.Light.surfaceSunken,

    outline = Tokens.Light.borderStrong,
    outlineVariant = Tokens.Light.border,

    error = Tokens.Light.danger,
    // The palette defines `danger` without naming a foreground for it.
    // `surface` is the light scheme's white and is what belongs on a
    // dark red — a literal here would be the one untraceable colour in
    // the app, which M6.2's criteria forbid.
    onError = Tokens.Light.surface,
    errorContainer = Tokens.Light.surfaceSunken,
    onErrorContainer = Tokens.Light.danger,

    scrim = Tokens.Light.textPrimary,
)

internal val SnDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Tokens.Dark.accent,
    onPrimary = Tokens.Dark.onAccent,
    primaryContainer = Tokens.Dark.surfaceRaised,
    onPrimaryContainer = Tokens.Dark.accentText,

    secondary = Tokens.Dark.accent,
    onSecondary = Tokens.Dark.onAccent,
    secondaryContainer = Tokens.Dark.surfaceRaised,
    onSecondaryContainer = Tokens.Dark.textPrimary,

    tertiary = Tokens.Dark.accent,
    onTertiary = Tokens.Dark.onAccent,
    tertiaryContainer = Tokens.Dark.surfaceRaised,
    onTertiaryContainer = Tokens.Dark.textPrimary,

    background = Tokens.Dark.surface,
    onBackground = Tokens.Dark.textPrimary,

    surface = Tokens.Dark.surface,
    onSurface = Tokens.Dark.textPrimary,
    surfaceVariant = Tokens.Dark.surfaceRaised,
    onSurfaceVariant = Tokens.Dark.textSecondary,

    // Dark raises with lighter tone, so sunken is BELOW the base surface
    // here while raised is above it — the inverse of the light scheme.
    surfaceContainerLowest = Tokens.Dark.surfaceSunken,
    surfaceContainerLow = Tokens.Dark.surface,
    surfaceContainer = Tokens.Dark.surfaceRaised,
    surfaceContainerHigh = Tokens.Dark.surfaceRaised,
    surfaceContainerHighest = Tokens.Dark.borderStrong,

    outline = Tokens.Dark.borderStrong,
    outlineVariant = Tokens.Dark.border,

    error = Tokens.Dark.danger,
    onError = Tokens.Dark.onAccent,
    errorContainer = Tokens.Dark.surfaceRaised,
    onErrorContainer = Tokens.Dark.danger,

    scrim = Tokens.Dark.surfaceSunken,
)
