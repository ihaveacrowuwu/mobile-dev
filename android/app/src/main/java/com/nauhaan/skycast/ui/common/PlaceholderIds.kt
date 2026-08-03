package com.nauhaan.skycast.ui.common

/**
 * Stand-in route arguments used by the placeholder screens.
 *
 * They exist only so the push destinations can be reached, and therefore
 * screenshotted and UI-tested, before the real lists that would supply genuine ids are
 * built. Every reference disappears as its screen is implemented.
 */

/** First row Room assigns, so this resolves to a real record once a location is saved. */
const val PreviewLocationId: Long = 1L

/** 2026-08-04 as an epoch day, the date this scaffold was created. */
const val PreviewEpochDay: Long = 20_670L
