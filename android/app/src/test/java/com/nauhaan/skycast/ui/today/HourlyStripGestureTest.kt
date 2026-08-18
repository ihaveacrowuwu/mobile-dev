package com.nauhaan.skycast.ui.today

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The hourly strip must keep its own horizontal gesture.
 *
 * Compose passes whatever a nested scrollable cannot use up to its parent, and the parent here is
 * the pager behind Today.
 *
 * Both halves are asserted. Consuming the horizontal remainder is what keeps a flick on the strip
 * from changing location; *not* consuming the vertical remainder is what keeps a drag up or down on
 * the strip scrolling the page.
 */
class HourlyStripGestureTest {
    @Test
    fun `leftover horizontal scroll is consumed so the pager never sees it`() {
        val available = Offset(x = -120f, y = 0f)

        val consumed = StripKeepsItsOwnGesture.onPostScroll(
            consumed = Offset.Zero,
            available = available,
            source = NestedScrollSource.UserInput,
        )

        assertEquals(available.x, consumed.x, 0.01f)
    }

    @Test
    fun `leftover vertical scroll is left for the page to use`() {
        val consumed = StripKeepsItsOwnGesture.onPostScroll(
            consumed = Offset.Zero,
            available = Offset(x = -120f, y = -80f),
            source = NestedScrollSource.UserInput,
        )

        assertEquals(0f, consumed.y, 0.01f)
    }

    @Test
    fun `a fling is treated the same way as a drag`() = runTest {
        // Without this half the gesture stops at the strip's end and then the *fling* carries on
        // into a page change, which looks identical to the original defect.
        val consumed = StripKeepsItsOwnGesture.onPostFling(
            consumed = Velocity.Zero,
            available = Velocity(x = -900f, y = -400f),
        )

        assertEquals(-900f, consumed.x, 0.01f)
        assertEquals(0f, consumed.y, 0.01f)
    }
}
