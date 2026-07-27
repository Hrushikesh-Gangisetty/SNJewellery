package com.snjewellery.admin.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.snjewellery.admin.R

/**
 * Typography, mapped from [Tokens.Type]. See docs/design/typography.md.
 *
 * Both families are **variable fonts** — one file per family carrying
 * every weight, rather than a static file per weight. That needs the
 * weight axis at runtime, which is API 26 and up; minSdk is exactly 26,
 * so it is available on every supported device (see
 * docs/architecture/android-build.md §2).
 *
 * Licences for both are in android/licenses/. Both are OFL, which
 * Resolved Decision 14 required.
 */

/**
 * Opted in narrowly, at the two call sites that need it, rather than
 * with a compiler flag — a module-wide opt-in would quietly admit every
 * other experimental text API too.
 *
 * The risk is bounded: if this signature changes, the fallback is static
 * font files per weight, which is a change to this file alone.
 */
@OptIn(ExperimentalTextApi::class)
private fun interWeight(weight: Int) = Font(
    R.font.inter_variable,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/**
 * Body and UI. Carries everything below the display serif's floor, which
 * is nearly all text in an admin app.
 *
 * Only the three weights typography.md §3 names are declared. A weight
 * that is not declared is synthesised by the renderer — faux-bold, which
 * looks visibly wrong next to a real cut.
 */
private val Inter = FontFamily(
    interWeight(Tokens.Type.BODY_M_WEIGHT),
    interWeight(Tokens.Type.SPEC_WEIGHT),
    interWeight(Tokens.Type.LABEL_WEIGHT),
)

/**
 * Display serif. **Never below 28sp** — [Tokens.Type.displayMin] is that
 * floor, and typography.md §1 explains it: high-contrast hairlines
 * weaken badly at small sizes and on low-DPI screens.
 *
 * Only the three display steps use it, and [displayStyle] enforces the
 * floor rather than trusting each call site.
 */
@OptIn(ExperimentalTextApi::class)
private val CormorantGaramond = FontFamily(
    Font(
        R.font.cormorant_garamond_variable,
        FontWeight(Tokens.Type.DISPLAY_XL_WEIGHT),
        variationSettings = FontVariation.Settings(
            FontVariation.weight(Tokens.Type.DISPLAY_XL_WEIGHT),
        ),
    ),
)

/**
 * A display step. Fails the build's own rule loudly rather than quietly
 * shipping a serif below its floor: typography.md §1 makes this a hard
 * limit, and a limit nothing enforces is a comment.
 */
private fun displayStyle(size: TextUnit, lineHeight: TextUnit, weight: Int): TextStyle {
    require(size.value >= Tokens.Type.displayMin.value) {
        "Display serif at ${size.value}sp is below the ${Tokens.Type.displayMin.value}sp " +
            "floor in typography.md §1. Use Inter at this size."
    }
    return TextStyle(
        fontFamily = CormorantGaramond,
        fontSize = size,
        lineHeight = lineHeight,
        fontWeight = FontWeight(weight),
    )
}

private fun bodyStyle(size: TextUnit, lineHeight: TextUnit, weight: Int) = TextStyle(
    fontFamily = Inter,
    fontSize = size,
    lineHeight = lineHeight,
    fontWeight = FontWeight(weight),
)

/**
 * The design system's scale expressed in Material 3's slot names.
 *
 * The mapping is a translation, not a redesign — Material has fifteen
 * slots and typography.md has eleven steps, so some slots repeat a step.
 * What matters is that every slot resolves to a token: a screen using
 * `MaterialTheme.typography.bodyLarge` gets `body-l` and cannot get
 * something invented.
 *
 * `display-xl` has no Material slot above `displayLarge`, so it is
 * exposed as [SnTypography.displayXl] instead — Material's scale simply
 * does not go that high.
 */
internal val SnTypography = Typography(
    displayLarge = displayStyle(
        Tokens.Type.displayLSize,
        Tokens.Type.displayLLineHeight,
        Tokens.Type.DISPLAY_L_WEIGHT,
    ),
    displayMedium = displayStyle(
        Tokens.Type.headingLSize,
        Tokens.Type.headingLLineHeight,
        Tokens.Type.HEADING_L_WEIGHT,
    ),
    displaySmall = displayStyle(
        Tokens.Type.headingLSize,
        Tokens.Type.headingLLineHeight,
        Tokens.Type.HEADING_L_WEIGHT,
    ),

    // Headings below the serif's floor are Inter, not a shrunken serif.
    headlineLarge = displayStyle(
        Tokens.Type.headingLSize,
        Tokens.Type.headingLLineHeight,
        Tokens.Type.HEADING_L_WEIGHT,
    ),
    headlineMedium = bodyStyle(
        Tokens.Type.headingMSize,
        Tokens.Type.headingMLineHeight,
        Tokens.Type.HEADING_M_WEIGHT,
    ),
    headlineSmall = bodyStyle(
        Tokens.Type.headingSSize,
        Tokens.Type.headingSLineHeight,
        Tokens.Type.HEADING_S_WEIGHT,
    ),

    titleLarge = bodyStyle(
        Tokens.Type.headingMSize,
        Tokens.Type.headingMLineHeight,
        Tokens.Type.HEADING_M_WEIGHT,
    ),
    titleMedium = bodyStyle(
        Tokens.Type.headingSSize,
        Tokens.Type.headingSLineHeight,
        Tokens.Type.HEADING_S_WEIGHT,
    ),
    titleSmall = bodyStyle(
        Tokens.Type.specSize,
        Tokens.Type.specLineHeight,
        Tokens.Type.SPEC_WEIGHT,
    ),

    bodyLarge = bodyStyle(
        Tokens.Type.bodyLSize,
        Tokens.Type.bodyLLineHeight,
        Tokens.Type.BODY_L_WEIGHT,
    ),
    bodyMedium = bodyStyle(
        Tokens.Type.bodyMSize,
        Tokens.Type.bodyMLineHeight,
        Tokens.Type.BODY_M_WEIGHT,
    ),
    bodySmall = bodyStyle(
        Tokens.Type.bodySSize,
        Tokens.Type.bodySLineHeight,
        Tokens.Type.BODY_S_WEIGHT,
    ),

    labelLarge = bodyStyle(
        Tokens.Type.bodySSize,
        Tokens.Type.bodySLineHeight,
        Tokens.Type.SPEC_WEIGHT,
    ),
    labelMedium = bodyStyle(
        Tokens.Type.captionSize,
        Tokens.Type.captionLineHeight,
        Tokens.Type.CAPTION_WEIGHT,
    ),
    labelSmall = bodyStyle(
        Tokens.Type.labelSize,
        Tokens.Type.labelLineHeight,
        Tokens.Type.LABEL_WEIGHT,
    ),
)

/**
 * The two steps Material's scale has no slot for. Reached through
 * `MaterialTheme` like everything else — see [SnTheme].
 */
internal object SnTextStyles {
    /** The largest step. Above Material's `displayLarge`. */
    val displayXl = displayStyle(
        Tokens.Type.displayXlSize,
        Tokens.Type.displayXlLineHeight,
        Tokens.Type.DISPLAY_XL_WEIGHT,
    )

    /**
     * Tabular figures for weights, counts and rates. `spec` in
     * typography.md — a column of numbers that do not line up reads as
     * carelessness on a screen about grams and rupees.
     */
    val spec = bodyStyle(
        Tokens.Type.specSize,
        Tokens.Type.specLineHeight,
        Tokens.Type.SPEC_WEIGHT,
    )

    /** Smallest step. Metadata and timestamps, never body copy. */
    val label = bodyStyle(
        Tokens.Type.labelSize,
        Tokens.Type.labelLineHeight,
        Tokens.Type.LABEL_WEIGHT,
    )
}
