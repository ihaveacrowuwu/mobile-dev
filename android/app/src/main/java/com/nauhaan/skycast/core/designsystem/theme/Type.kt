package com.nauhaan.skycast.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography built on the Material 3 scale.
 *
 * Sizes are declared in **sp**, never dp, so they scale with the user's font-size
 * setting, and
 * `displayLarge` below is deliberately the only oversized style, used for the single
 * hero temperature reading.
 */
internal val SkyCastTypography =
    Typography().let { default ->
        Typography(
            displayLarge = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Light,
                fontSize = 88.sp,
                lineHeight = 96.sp,
                // Large light type reads better slightly tightened.
                letterSpacing = (-2).sp,
            ),
            displayMedium = default.displayMedium,
            displaySmall = default.displaySmall,
            headlineLarge = default.headlineLarge,
            headlineMedium = default.headlineMedium,
            headlineSmall = default.headlineSmall,
            titleLarge = default.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            titleMedium = default.titleMedium,
            titleSmall = default.titleSmall,
            bodyLarge = default.bodyLarge,
            bodyMedium = default.bodyMedium,
            bodySmall = default.bodySmall,
            labelLarge = default.labelLarge,
            labelMedium = default.labelMedium,
            labelSmall = default.labelSmall,
        )
    }

/** Corner shapes, driven by [Radius] so the scale stays in one place. */
internal val SkyCastShapes =
    Shapes(
        extraSmall = RoundedCornerShape(Radius.sm),
        small = RoundedCornerShape(Radius.sm),
        medium = RoundedCornerShape(Radius.md),
        large = RoundedCornerShape(Radius.lg),
        extraLarge = RoundedCornerShape(Radius.lg),
    )
