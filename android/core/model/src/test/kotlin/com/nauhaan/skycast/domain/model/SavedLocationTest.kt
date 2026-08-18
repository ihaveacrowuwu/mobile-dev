package com.nauhaan.skycast.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The saved-location cap.
 *
 * Worth its own test because it is an off-by-one waiting to happen, and because two callers depend on
 * it agreeing with itself: `LocationRepositoryImpl.save` refuses past the cap, and the Locations screen
 * hides its Add button at the same point. If those two disagreed, the button would still be there and
 * would lead only to an error.
 */
class SavedLocationTest {
    @Test
    fun `there is room below the cap, and none at it`() {
        assertTrue(SavedLocation.canSaveAnother(0))
        assertTrue(SavedLocation.canSaveAnother(SavedLocation.MAX_SAVED - 1))
        // The boundary: with MAX_SAVED already stored the list is full, not "one more allowed".
        assertFalse(SavedLocation.canSaveAnother(SavedLocation.MAX_SAVED))
        // Defensive: a count past the cap (a database edited by hand, a migration) must not reopen it.
        assertFalse(SavedLocation.canSaveAnother(SavedLocation.MAX_SAVED + 1))
    }

    @Test
    fun `the cap is ten`() {
        // Pinned. The number is recorded in the model's KDoc and repeated in the README, so a
        // silent change should break something.
        assertTrue(SavedLocation.MAX_SAVED == 10)
    }
}
