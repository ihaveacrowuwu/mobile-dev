package com.nauhaan.skycast.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * The app's card surface: a **frosted, translucent** panel that the weather shows through.
 *
 * Translucency plus a lit rim, not a true blur: Compose has no backdrop-blur primitive, since
 * `Modifier.blur` blurs a composable's *own* content and `RenderEffect` needs API 31 while this app
 * supports 26.
 */
@Composable
fun frostedCardColours(): CardColors {
    val container = frostedContainerColour()
    return CardDefaults.cardColors(
        containerColor = container,
        // Taken from the opaque surface, not from the translucent container: `contentColorFor` on a
        // half-transparent colour has no matching role and falls back to `Color.Unspecified`, which
        // renders body text black in dark mode.
        contentColor = contentColorFor(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

/**
 * The translucent fill behind a card.
 *
 * **No weather tint.** The fill is light enough that what shows through is the background itself,
 * so the card cannot clash with the gradient behind it.
 *
 * Animated on the **effects** spec for the theme changes that do move it: this is a colour, and the
 * spatial spec would overshoot past the target.
 */
@Composable
fun frostedContainerColour(): Color {
    val container by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = FROST_ALPHA),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "frostedContainer",
    )
    return container
}

/**
 * A rim that catches the light at the top and fades away by the bottom.
 *
 * It is what makes the panel read as a pane with an edge rather than as a lighter rectangle, and it
 * works in both themes because it adds light rather than assuming a dark ground.
 */
fun Modifier.frostRim(shape: Shape): Modifier = border(
    width = RimWidth,
    brush = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = RIM_ALPHA), Color.Transparent),
    ),
    shape = shape,
)

/** Cards carry no shadow now: an elevation shadow under a translucent panel reads as grime. */
@Composable
fun frostedCardElevation() = CardDefaults.cardElevation(defaultElevation = 0.dp)

/**
 * How much of the surface colour survives.
 *
 * Low. It is a veil that lifts the card off the background far enough to read text on, not a fill, at 0.55
 * the card was a pale slab with a hint of weather behind it, which is the opposite emphasis.
 */
private const val FROST_ALPHA = 0.32f

private const val RIM_ALPHA = 0.35f
private val RimWidth = 1.dp
