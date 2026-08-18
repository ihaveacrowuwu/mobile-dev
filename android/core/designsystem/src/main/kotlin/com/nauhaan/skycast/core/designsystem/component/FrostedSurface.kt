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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import com.nauhaan.skycast.core.designsystem.theme.LocalWeatherTint

/**
 * The app's card surface: a **frosted, translucent** panel that the weather shows through.
 *
 * ## What "frosted" means on Android, honestly
 *
 * iOS gets a real material, which blurs whatever is behind it. Compose has no backdrop-blur
 * primitive: `Modifier.blur` blurs a composable's *own* content, and `RenderEffect` needs API 31
 * while this app supports 26. The libraries that solve it properly would be a new dependency, which
 * the no-new-dependencies rule argues against for something cosmetic.
 *
 * So this is translucency plus a lit rim rather than true blur. The weather background behind it is a
 * soft gradient wash with no hard edges, which is exactly the case where translucency alone reads as
 * frosted glass, there is no fine detail behind the card for a blur to soften. The effect is close
 * enough that the two platforms look like the same app.
 *
 * ## The translucent fill
 *
 * The cards were `surfaceContainerHigh` with the location's container colour mixed in. That made them
 * *coloured* rather than *translucent*, so a card sat on the weather background like a sticker instead
 * of a pane in front of it. The tint survives at lower strength, enough that swiping between a clear
 * place and an overcast one still shifts the cards.
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
 * The translucent, weather-tinted fill behind a card.
 *
 * Animated on the **effects** spec: this is a colour change, and the spatial spec would overshoot past
 * the target colour, which looks like a bug rather than a flourish.
 */
@Composable
fun frostedContainerColour(): Color {
    val tint = LocalWeatherTint.current
    val base = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = FROST_ALPHA)
    val target = if (tint == null) base else tint.copy(alpha = TINT_ALPHA).compositeOver(base)
    val container by animateColorAsState(
        targetValue = target,
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

/** How much of the surface colour survives. Low enough to see the weather, high enough to read text on. */
private const val FROST_ALPHA = 0.55f

/** Low, because the translucency is now doing the work the colour used to do. */
private const val TINT_ALPHA = 0.22f

private const val RIM_ALPHA = 0.35f
private val RimWidth = 1.dp
