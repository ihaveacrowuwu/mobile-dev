package com.nauhaan.skycast.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nauhaan.skycast.core.designsystem.theme.SkyCastTheme
import com.nauhaan.skycast.core.designsystem.theme.Spacing
import com.nauhaan.skycast.domain.model.MoonCalculator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Moon, drawn at its actual phase.
 *
 * The terminator is computed from the same elongation the heading is, so the drawing and the text
 * can never disagree.
 *
 * ## The geometry
 *
 * A lit lunar disc is bounded by two curves: the **limb**, a semicircle of radius *R*, and the
 * **terminator**, the day/night line, which projects to a half-ellipse of semi-axis *R*·cos θ where θ
 * is the elongation. The sign of that cosine is what makes one formula cover the whole month:
 * positive gives a crescent bulging away from the limb, negative gives a gibbous moon bulging past
 * the centre, and zero gives the straight edge of a quarter moon.
 *
 * The Swift twin is `MoonVisuals.swift`, and the two draw from identical arithmetic.
 *
 * @param elongationDegrees 0 new, 90 first quarter, 180 full, 270 last quarter.
 * @param showsDetail the glow and the maria. Off for the small discs in the "coming up" list, where
 *   they would be sub-pixel noise.
 */
@Composable
fun MoonDisc(elongationDegrees: Double, diameter: Dp, modifier: Modifier = Modifier, showsDetail: Boolean = true) {
    Canvas(
        modifier = modifier
            .size(diameter)
            // One drawing, described once by whatever contains it.
            .clearAndSetSemantics { },
    ) {
        val radius = size.minDimension / 2f
        val centre = Offset(size.width / 2f, size.height / 2f)

        if (showsDetail) {
            // Moonlight. Scaled with the lit fraction, so a new moon does not glow.
            val lit = MoonCalculator.illuminatedFraction(elongationDegrees).toFloat()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Glow.copy(alpha = GLOW_BASE_ALPHA + GLOW_LIT_ALPHA * lit),
                        Color.Transparent,
                    ),
                    center = centre,
                    radius = radius * GLOW_RADIUS,
                ),
                radius = radius * GLOW_RADIUS,
                center = centre,
            )
        }

        // The unlit disc, kept faintly visible for earthshine.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(DarkSide, DarkSideEdge),
                center = Offset(centre.x - radius * SHADE_OFFSET, centre.y - radius * SHADE_OFFSET),
                radius = radius * SHADE_SPREAD,
            ),
            radius = radius,
            center = centre,
        )

        // The lit region, and the maria clipped to it.
        val lit = litPath(elongationDegrees, centre, radius)
        drawPath(
            path = lit,
            brush = Brush.radialGradient(
                colors = listOf(LitCentre, LitEdge),
                center = Offset(centre.x - radius * SHADE_OFFSET, centre.y - radius * SHADE_OFFSET),
                radius = radius * SHADE_SPREAD,
            ),
        )
        if (showsDetail) {
            clipPath(lit) { drawMaria(centre, radius) }
        }

        // A hairline limb, so the disc still has an edge against a bright sky.
        drawCircle(
            color = Limb,
            radius = radius,
            center = centre,
            style = Stroke(width = 1f, cap = StrokeCap.Round),
        )
    }
}

/**
 * The lit region of the disc at a given elongation.
 *
 * Traced as the limb from top to bottom, then the terminator back from bottom to top, sampled at
 * [TERMINATOR_STEPS] points along each curve.
 */
private fun litPath(elongationDegrees: Double, centre: Offset, radius: Float): Path {
    val theta = elongationDegrees * PI / HALF_TURN
    // Signed semi-axis of the terminator: +R at new, 0 at the quarters, −R at full.
    val terminatorAxis = radius * cos(theta).toFloat()
    // Waxing moons are lit on the right in the northern hemisphere, waning on the left.
    val side = if (elongationDegrees < HALF_TURN) 1f else -1f

    return Path().apply {
        moveTo(centre.x, centre.y - radius)
        for (step in 0..TERMINATOR_STEPS) {
            val angle = step.toDouble() / TERMINATOR_STEPS * PI
            lineTo(
                centre.x + side * radius * sin(angle).toFloat(),
                centre.y - radius * cos(angle).toFloat(),
            )
        }
        for (step in TERMINATOR_STEPS downTo 0) {
            val angle = step.toDouble() / TERMINATOR_STEPS * PI
            lineTo(
                centre.x + side * terminatorAxis * sin(angle).toFloat(),
                centre.y - radius * cos(angle).toFloat(),
            )
        }
        close()
    }
}

/**
 * The maria, as soft grey blots at fixed positions so the face does not reshuffle between
 * recompositions. They roughly follow the near side: Tranquillitatis and Imbrium upper left,
 * Crisium right.
 */
private fun DrawScope.drawMaria(centre: Offset, radius: Float) {
    for (mare in Maria) {
        val centreOfMare = Offset(centre.x + radius * mare.x, centre.y + radius * mare.y)
        val mareRadius = radius * mare.size
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    MareTint.copy(alpha = mare.alpha),
                    MareTint.copy(alpha = mare.alpha * MARE_EDGE_ALPHA),
                    Color.Transparent,
                ),
                center = centreOfMare,
                radius = mareRadius,
            ),
            radius = mareRadius,
            center = centreOfMare,
        )
    }
}

private data class Mare(val x: Float, val y: Float, val size: Float, val alpha: Float)

private val Maria = listOf(
    Mare(-0.28f, -0.32f, 0.30f, 0.22f),
    Mare(0.12f, -0.48f, 0.20f, 0.16f),
    Mare(0.48f, -0.10f, 0.16f, 0.20f),
    Mare(-0.10f, 0.28f, 0.26f, 0.14f),
    Mare(0.32f, 0.52f, 0.13f, 0.17f),
    Mare(-0.52f, 0.20f, 0.12f, 0.15f),
)

/**
 * The night sky the Moon hangs in. Dark in **both** themes, since it is imagery rather than a card.
 */
@Composable
fun NightSkyPanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = Spacing.lg),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SkyCorner))
            .nightSky()
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * Paints the night sky behind whatever this modifies: the whole screen, or the small circular
 * patches behind the discs in the "coming up" list.
 */
fun Modifier.nightSky(): Modifier = drawBehind {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Zenith, Horizon),
            startY = 0f,
            endY = size.height,
        ),
    )
    drawStars(size)
}

/**
 * Stars at fixed positions, generated once from a fixed seed so the layout is stable across
 * recompositions. The same generator and seed as the Swift side, so the two skies match.
 */
private fun DrawScope.drawStars(size: Size) {
    for (star in Stars) {
        drawCircle(
            color = Color.White.copy(alpha = star.alpha),
            radius = star.size,
            center = Offset(star.x * size.width, star.y * size.height),
        )
    }
}

private data class Star(val x: Float, val y: Float, val size: Float, val alpha: Float)

private val Stars: List<Star> = buildList {
    var seed = 0x5CA57CA5UL
    fun next(): Float {
        seed = seed * 6_364_136_223_846_793_005UL + 1_442_695_040_888_963_407UL
        return ((seed shr 11).toDouble() / (1UL shl 53).toDouble()).toFloat()
    }
    repeat(STAR_COUNT) {
        add(
            Star(
                x = next(),
                y = next(),
                size = STAR_MIN_SIZE + next() * STAR_SIZE_RANGE,
                alpha = STAR_MIN_ALPHA + next() * STAR_ALPHA_RANGE,
            ),
        )
    }
}

/**
 * Progress through the lunar month, as a ring with the four principal phases marked.
 *
 * @param cycleFraction 0 at new moon, 1 at the next new moon.
 */
@Composable
fun LunarCycleRing(cycleFraction: Float, diameter: Dp, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .size(diameter)
            .clearAndSetSemantics { },
    ) {
        val radius = size.minDimension / 2f - RingStrokePx
        val centre = Offset(size.width / 2f, size.height / 2f)
        val fraction = cycleFraction.coerceIn(0f, 1f)

        drawCircle(
            color = Color.White.copy(alpha = RING_TRACK_ALPHA),
            radius = radius,
            center = centre,
            style = Stroke(width = RingStrokePx),
        )

        // Sweeps from the top, because that is where the month starts.
        drawArc(
            brush = Brush.sweepGradient(listOf(RingStart, RingMid, RingStart)),
            startAngle = -QUARTER_TURN,
            sweepAngle = fraction * FULL_TURN,
            useCenter = false,
            topLeft = Offset(centre.x - radius, centre.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = RingStrokePx, cap = StrokeCap.Round),
        )

        for (position in listOf(0f, 0.25f, 0.5f, 0.75f)) {
            drawCircle(
                color = Color.White.copy(alpha = RING_TICK_ALPHA),
                radius = TickRadiusPx,
                center = pointOnRing(centre, radius, position),
            )
        }

        drawCircle(
            color = Color.White,
            radius = MarkerRadiusPx,
            center = pointOnRing(centre, radius, fraction),
        )
    }
}

/** A point at [fraction] around the ring, measured clockwise from the top. */
private fun pointOnRing(centre: Offset, radius: Float, fraction: Float): Offset {
    val angle = (fraction * FULL_TURN - QUARTER_TURN) * PI / HALF_TURN
    return Offset(
        centre.x + radius * cos(angle).toFloat(),
        centre.y + radius * sin(angle).toFloat(),
    )
}

// Fixed, not theme-derived: these are the colours of the Moon and of moonlight, and they are the same
// in a light interface as in a dark one. The disc always sits on the night-sky panel, so it has a
// guaranteed dark ground in either theme.
private val LitCentre = Color(0xFFFAF8EE)
private val LitEdge = Color(0xFFCCC9C2)
private val DarkSide = Color(0xFF212433)
private val DarkSideEdge = Color(0xFF12141F)
private val MareTint = Color(0xFF6B6D78)
private val Limb = Color(0x2EFFFFFF)
private val Glow = Color(0xFFD9E3FF)
private val Zenith = Color(0xFF0D0F24)
private val Horizon = Color(0xFF1C2140)
private val RingStart = Color(0xFF737FD9)
private val RingMid = Color(0xFFF2F0DC)

private val SkyCorner = 24.dp
private const val RingStrokePx = 6f
private const val TickRadiusPx = 4f
private const val MarkerRadiusPx = 9f
private const val RING_TRACK_ALPHA = 0.16f
private const val RING_TICK_ALPHA = 0.45f

private const val GLOW_BASE_ALPHA = 0.10f
private const val GLOW_LIT_ALPHA = 0.45f
private const val GLOW_RADIUS = 1.7f
private const val SHADE_OFFSET = 0.22f
private const val SHADE_SPREAD = 1.4f
private const val TERMINATOR_STEPS = 48

/** How far the soft edge of a mare fades before it reaches transparent. */
private const val MARE_EDGE_ALPHA = 0.45f
private const val FULL_TURN = 360f
private const val HALF_TURN = 180.0
private const val QUARTER_TURN = 90f

private const val STAR_COUNT = 70
private const val STAR_MIN_SIZE = 0.9f
private const val STAR_SIZE_RANGE = 1.9f
private const val STAR_MIN_ALPHA = 0.20f
private const val STAR_ALPHA_RANGE = 0.55f

@Preview(showBackground = true)
@Composable
private fun MoonDiscPreview() {
    SkyCastTheme {
        NightSkyPanel {
            MoonDisc(elongationDegrees = 76.0, diameter = 160.dp)
        }
    }
}
