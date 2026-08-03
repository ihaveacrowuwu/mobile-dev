package com.nauhaan.skycast.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The 4 dp spacing scale.
 *
 * Every padding, gap and inset in the app comes from here. A fixed scale is what makes
 * the layout look deliberate rather than nudged, and it means a spacing change is one
 * edit instead of a hunt through every screen.
 *
 * Never write a raw `12.dp` in a composable, detekt's `MagicNumber` rule will flag it.
 */
object Spacing {
    /** 2 dp, hairline separation, e.g. between a label and its value. */
    val xxs: Dp = 2.dp

    /** 4 dp, tight grouping inside a single component. */
    val xs: Dp = 4.dp

    /** 8 dp, related items within a card. */
    val sm: Dp = 8.dp

    /** 16 dp, the default. Screen edge insets and gaps between cards. */
    val md: Dp = 16.dp

    /** 24 dp, separation between distinct sections. */
    val lg: Dp = 24.dp

    /** 32 dp, major visual breaks. */
    val xl: Dp = 32.dp

    /** 48 dp, around empty-state and error illustrations. */
    val xxl: Dp = 48.dp
}

/** Corner radii, kept consistent so cards and sheets share a visual language. */
object Radius {
    val sm: Dp = 8.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val pill: Dp = 999.dp
}

/**
 * Minimum interactive sizes.
 *
 * 48 dp is the Material accessibility minimum for a touch target. Anything the user
 * can tap must be at least this big, even when the visible art is smaller, this is
 * directly assessed under UI/UX.
 */
object TouchTarget {
    val minimum: Dp = 48.dp
}
