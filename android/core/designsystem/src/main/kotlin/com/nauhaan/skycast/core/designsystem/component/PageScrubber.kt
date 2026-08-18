package com.nauhaan.skycast.core.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nauhaan.skycast.core.designsystem.theme.SkyCastTheme
import com.nauhaan.skycast.core.designsystem.theme.Spacing
import kotlin.math.roundToInt

/**
 * The page indicator for [androidx.compose.foundation.pager.HorizontalPager]: dots you can drag.
 *
 * Compose has no `UIPageControl` equivalent, so the drag, the mapping from x to page, and the
 * haptic are all written here.
 *
 * The haptic fires on each *change* of page, not on each drag event, so it is a tick per dot rather
 * than a buzz. [HapticFeedbackType.SegmentTick] is the same feedback a slider gives crossing a
 * notch.
 *
 * The dots are not individual touch targets: at 8 dp they are far below the 48 dp minimum, so the
 * whole row is one wide draggable strip and the menu in the app bar jumps directly to a place.
 */
@Composable
fun PageScrubber(
    count: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    if (count <= 1) return

    val haptics = LocalHapticFeedback.current
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = INACTIVE_ALPHA)

    // A floating pill rather than bare dots on the page, matching the control Apple's Weather app
    // puts at the bottom of its screen, the iOS counterpart wraps `UIPageControl` in glass for the
    // same reason. Bare dots on a photographic or heavily washed background have no ground of their
    // own, so they lose contrast exactly when the weather behind them is most colourful. It also
    // gives the drag gesture a visible extent: a control looks grabbable in a way three dots do not.
    Surface(
        modifier = modifier.frostRim(CircleShape),
        shape = CircleShape,
        color = frostedContainerColour(),
    ) {
        ScrubberDots(
            count = count,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            contentDescription = contentDescription,
            haptics = haptics,
            active = active,
            inactive = inactive,
        )
    }
}

@Composable
private fun ScrubberDots(
    count: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    contentDescription: String,
    haptics: HapticFeedback,
    active: Color,
    inactive: Color,
) {
    Row(
        modifier = Modifier
            .height(RowHeight)
            .clearAndSetSemantics { this.contentDescription = contentDescription }
            .pointerInput(count) {
                // The row's own width maps to the whole set of pages, so a drag from one end to the
                // other passes through every one of them regardless of how many there are.
                var lastReported = selectedIndex
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val page = pageFor(offset.x, size.width, count)
                        if (page != lastReported) {
                            lastReported = page
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(page)
                        }
                    },
                ) { change, _ ->
                    val page = pageFor(change.position.x, size.width, count)
                    if (page != lastReported) {
                        lastReported = page
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(page)
                    }
                }
            }
            // Wider inside the pill than it was bare: the control needs room around the dots or it
            // reads as a badge clamped to them.
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        repeat(count) { index ->
            val isSelected = index == selectedIndex
            // Geometry, so the spatial spec: a dot growing slightly past its size and settling is
            // the Expressive treatment. Colour is left to swap outright, since animating both at
            // once on something this small reads as a smudge.
            val diameter by animateDpAsState(
                targetValue = if (isSelected) SelectedDot else Dot,
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                label = "pageDot",
            )
            Box(
                modifier = Modifier
                    .size(diameter)
                    .clip(CircleShape)
                    .background(if (isSelected) active else inactive),
            )
        }
    }
}

/** Which page the x position within a row of [width] pixels falls in. Internal for its test. */
internal fun pageFor(x: Float, width: Int, count: Int): Int {
    if (width <= 0) return 0
    val fraction = (x / width).coerceIn(0f, 1f)
    // `count - 1` and rounding, rather than `count` and truncating: dragging to the far right must
    // land on the last page, and truncation only reaches it exactly at the edge pixel.
    return (fraction * (count - 1)).roundToInt().coerceIn(0, count - 1)
}

private val Dot = 6.dp
private val SelectedDot = 9.dp
private val RowHeight = 28.dp
private const val INACTIVE_ALPHA = 0.35f

@Preview(showBackground = true)
@Composable
private fun PageScrubberPreview() {
    SkyCastTheme {
        PageScrubber(
            count = 4,
            selectedIndex = 1,
            onSelect = {},
            contentDescription = "Showing Malé, 2 of 4",
            modifier = Modifier.padding(Spacing.md),
        )
    }
}
