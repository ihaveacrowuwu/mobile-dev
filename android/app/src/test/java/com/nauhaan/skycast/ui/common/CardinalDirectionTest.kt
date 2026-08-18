package com.nauhaan.skycast.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The compass names a bearing the numeric value never mentions, so a wrong table would be invisible
 * on screen: a needle pointing north-east beside the word "east" looks perfectly fine.
 *
 * Mirrors `ReadingPresentationTests` on iOS, including the values, so the two platforms cannot
 * disagree about which way the wind is blowing.
 */
class CardinalDirectionTest {
    @Test
    fun `cardinal points name the right sector`() {
        val expected = mapOf(
            0 to "N", 360 to "N", 90 to "E", 180 to "S", 270 to "W",
            23 to "NNE", 45 to "NE", 293 to "WNW", 338 to "NNW",
        )
        expected.forEach { (degrees, point) ->
            assertEquals("$degrees°", point, cardinalFor(degrees))
        }
    }

    @Test
    fun `out-of-range bearings wrap instead of crashing`() {
        // The API documents 0–360, but 360 and negatives are both representable, and an
        // out-of-range index would take down the whole screen rather than mislabel one tile.
        listOf(-90, 720, 450, -361).forEach { degrees ->
            assertTrue(cardinalFor(degrees).isNotEmpty())
        }
    }
}
