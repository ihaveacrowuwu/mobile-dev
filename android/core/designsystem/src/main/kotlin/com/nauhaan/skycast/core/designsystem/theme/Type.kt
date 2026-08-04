package com.nauhaan.skycast.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography built on the Material 3 **Expressive** scale.
 *
 * Sizes are declared in **sp**, never dp, so they scale with the user's font-size setting.
 *
 * Expressive adds *emphasized* variants of every role (`displayLargeEmphasized`,
 * `titleLargeEmphasized`, …), heavier and tighter than the plain roles, for the one or two elements
 * on a screen that carry the most meaning. Here that is the hero temperature and each screen's
 * title.
 *
 * Only the customised roles are overridden; every other role, plain and emphasized alike, keeps its
 * Expressive default by virtue of `copy()`.
 */
internal val SkyCastTypography: Typography =
    Typography().let { default ->
        default.copy(
            // The single hero reading. The emphasized display role already carries the
            // right weight and tracking; only size and letter spacing are tuned, because
            // 88sp of light type reads better slightly tightened.
            displayLargeEmphasized =
            default.displayLargeEmphasized.copy(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Light,
                fontSize = 88.sp,
                lineHeight = 96.sp,
                letterSpacing = (-2).sp,
            ),
        )
    }

/**
 * Corner shapes.
 *
 * Expressive uses a **less uniform, larger** corner language than baseline Material 3. Driven by
 * [Radius] so the scale stays in one place.
 */
internal val SkyCastShapes =
    Shapes(
        extraSmall = RoundedCornerShape(Radius.xs),
        small = RoundedCornerShape(Radius.sm),
        medium = RoundedCornerShape(Radius.md),
        large = RoundedCornerShape(Radius.lg),
        extraLarge = RoundedCornerShape(Radius.xl),
    )
