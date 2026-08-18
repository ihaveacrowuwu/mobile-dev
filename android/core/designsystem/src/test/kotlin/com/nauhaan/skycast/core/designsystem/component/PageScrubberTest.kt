package com.nauhaan.skycast.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The arithmetic that decides where a drag across the indicator lands.
 *
 * Worth pinning because the obvious version is wrong at one end: mapping the fraction onto `count`
 * and truncating only reaches the last page on the single edge pixel, so dragging fully to the
 * right stops one short. Mapping onto `count - 1` and rounding puts each dot at the centre of its
 * own share of the row, which is what a finger expects.
 */
class PageScrubberTest {
    @Test
    fun `the ends of the row are the first and last pages`() {
        assertEquals(0, pageFor(x = 0f, width = 300, count = 4))
        assertEquals(3, pageFor(x = 300f, width = 300, count = 4))
    }

    @Test
    fun `dragging beyond either edge clamps rather than overshooting`() {
        assertEquals(0, pageFor(x = -80f, width = 300, count = 4))
        assertEquals(3, pageFor(x = 900f, width = 300, count = 4))
    }

    @Test
    fun `each page owns the span around its own dot`() {
        // Four pages across 300px: centres at 0, 100, 200, 300. A point nearer 100 than 0 is page 1.
        assertEquals(1, pageFor(x = 90f, width = 300, count = 4))
        assertEquals(2, pageFor(x = 160f, width = 300, count = 4))
    }

    @Test
    fun `a single page is always page zero`() {
        // Guards the `count - 1` divisor: with one page that is zero, and dividing by it would
        // otherwise produce NaN and crash on the coerce.
        assertEquals(0, pageFor(x = 150f, width = 300, count = 1))
    }

    @Test
    fun `an unmeasured row reports the first page rather than dividing by zero`() {
        assertEquals(0, pageFor(x = 10f, width = 0, count = 4))
    }
}
