package com.snjewellery.admin.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * GENERATED FROM docs/design/tokens.json - DO NOT EDIT BY HAND
 *
 * Regenerate:  node tools/generate-tokens.mjs
 * Source:      docs/design/tokens.json
 *
 * These are raw token values. M6.2 maps them into a Material 3
 * ColorScheme and Typography. Screens must reference the theme,
 * never this object directly.
 *
 * Text sizes are in sp so the user's system font-size preference is
 * respected; everything else is dp.
 */
object Tokens {
    object Light {
        val surface = Color(0xFFFFFFFF)
        val surfaceRaised = Color(0xFFFAF9F7)
        val surfaceSunken = Color(0xFFF5F3F0)
        val textPrimary = Color(0xFF1C1917)
        val textSecondary = Color(0xFF57534E)
        val textMuted = Color(0xFF6B645F)
        val border = Color(0xFFE7E5E4)
        val borderStrong = Color(0xFFD6D3D1)
        val borderInteractive = Color(0xFF96908B)
        val accent = Color(0xFFC9A227)
        val accentText = Color(0xFF856404)
        val onAccent = Color(0xFF1C1917)
        val focus = Color(0xFF1C1917)
        val success = Color(0xFF15803D)
        val danger = Color(0xFFB91C1C)
    }

    object Dark {
        val surface = Color(0xFF0F0F0F)
        val surfaceRaised = Color(0xFF1A1917)
        val surfaceSunken = Color(0xFF080808)
        val textPrimary = Color(0xFFFAF9F7)
        val textSecondary = Color(0xFFA8A29E)
        val textMuted = Color(0xFF8A8580)
        val border = Color(0xFF292524)
        val borderStrong = Color(0xFF3F3B39)
        val borderInteractive = Color(0xFF6B6560)
        val accent = Color(0xFFD4AF37)
        val accentText = Color(0xFFE0BC4A)
        val onAccent = Color(0xFF0F0F0F)
        val focus = Color(0xFFFAF9F7)
        val success = Color(0xFF4ADE80)
        val danger = Color(0xFFF87171)
    }

    object Space {
        val s0 = 0.dp
        val s1 = 4.dp
        val s2 = 8.dp
        val s3 = 12.dp
        val s4 = 16.dp
        val s5 = 20.dp
        val s6 = 24.dp
        val s8 = 32.dp
        val s10 = 40.dp
        val s12 = 48.dp
        val s16 = 64.dp
        val s20 = 80.dp
        val s24 = 96.dp
        val s32 = 128.dp
    }

    object Radius {
        val none = 0.dp
        val sm = 2.dp
        val md = 4.dp
        val lg = 8.dp
        val full = 9999.dp
    }

    object Type {
        const val DISPLAY_FAMILY = "Cormorant Garamond"
        const val BODY_FAMILY = "Inter"
        /** Hard floor for the display serif - see typography.md #1. */
        val displayMin = 28.sp

        val displayXlSize = 56.sp
        val displayXlLineHeight = 62.sp
        const val DISPLAY_XL_WEIGHT = 500
        val displayLSize = 44.sp
        val displayLLineHeight = 51.sp
        const val DISPLAY_L_WEIGHT = 500
        val headingLSize = 28.sp
        val headingLLineHeight = 35.sp
        const val HEADING_L_WEIGHT = 500
        val headingMSize = 22.sp
        val headingMLineHeight = 30.sp
        const val HEADING_M_WEIGHT = 600
        val headingSSize = 18.sp
        val headingSLineHeight = 25.sp
        const val HEADING_S_WEIGHT = 600
        val bodyLSize = 18.sp
        val bodyLLineHeight = 29.sp
        const val BODY_L_WEIGHT = 400
        val bodyMSize = 16.sp
        val bodyMLineHeight = 26.sp
        const val BODY_M_WEIGHT = 400
        val bodySSize = 14.sp
        val bodySLineHeight = 21.sp
        const val BODY_S_WEIGHT = 400
        val captionSize = 13.sp
        val captionLineHeight = 19.sp
        const val CAPTION_WEIGHT = 400
        val labelSize = 12.sp
        val labelLineHeight = 16.sp
        const val LABEL_WEIGHT = 600
        val specSize = 16.sp
        val specLineHeight = 24.sp
        const val SPEC_WEIGHT = 500
    }

    object Motion {
        const val INSTANT_MS = 0L
        const val FAST_MS = 120L
        const val BASE_MS = 200L
        const val SLOW_MS = 320L
        const val DELIBERATE_MS = 480L
    }

    object Layout {
        val containerProse = 680.dp
        val containerContent = 1280.dp
        val containerWide = 1536.dp
        /** Material 3 minimum touch target. */
        val touchTarget = 48.dp
    }
}
