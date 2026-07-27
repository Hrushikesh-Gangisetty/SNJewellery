package com.snjewellery.admin.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

/**
 * Corner radii, from [Tokens.Radius]. See docs/design/layout.md.
 *
 * The scale is deliberately tight — 2, 4, 8dp. brand.md calls for
 * restraint, and a heavily rounded card reads as a consumer app rather
 * than a jeweller. Material's defaults are far rounder than this, which
 * is most of the reason overriding the shapes matters at all.
 *
 * `extraLarge` maps to `lg` rather than something larger: the scale has
 * no step above 8dp except `full`, and inventing one to fill a Material
 * slot would be a design value created outside the token pipeline.
 */
internal val SnShapes = Shapes(
    extraSmall = RoundedCornerShape(Tokens.Radius.sm),
    small = RoundedCornerShape(Tokens.Radius.sm),
    medium = RoundedCornerShape(Tokens.Radius.md),
    large = RoundedCornerShape(Tokens.Radius.lg),
    extraLarge = RoundedCornerShape(Tokens.Radius.lg),
)
