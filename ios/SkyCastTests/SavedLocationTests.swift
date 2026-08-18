import Foundation
import Testing
@testable import SkyCast

/// The saved-location cap.
///
/// Worth its own suite because it is an off-by-one waiting to happen, and because two callers depend on it
/// agreeing with itself: `LocationRepositoryImpl.save` refuses past the cap, and the Locations screen hides
/// its Add button at the same point. If those disagreed, the button would still be there and would lead
/// only to an error.
///
/// The Kotlin twin is `SavedLocationTest.kt`, asserting the same boundaries.
@Suite("Saved location cap")
struct SavedLocationTests {
    @Test("There is room below the cap, and none at it")
    func capBoundary() {
        #expect(SavedLocation.canSaveAnother(currentCount: 0))
        #expect(SavedLocation.canSaveAnother(currentCount: SavedLocation.maxSaved - 1))
        // The boundary: with maxSaved already stored the list is full, not "one more allowed".
        #expect(!SavedLocation.canSaveAnother(currentCount: SavedLocation.maxSaved))
        // Defensive: a count past the cap must not reopen it.
        #expect(!SavedLocation.canSaveAnother(currentCount: SavedLocation.maxSaved + 1))
    }

    @Test("The cap is ten")
    func capIsTen() {
        // Pinned. The number is recorded in the model's doc comment and repeated in the README, so
        // a silent change should break something.
        #expect(SavedLocation.maxSaved == 10)
    }

    @Test("Reaching the limit is not retryable, and says what to do")
    func limitErrorIsNotRetryable() {
        let error = AppError.locationLimitReached(limit: 10)
        // A Retry button here could never succeed; the message has to carry the instruction instead.
        #expect(!error.isRetryable)
        #expect(error.message.contains("Remove one"))
    }
}

/// ``SelectedLocationStore``'s fallback order, which is what stops the non-Home tabs showing an empty
/// state before Home has ever been on screen.
@Suite("Selected location")
@MainActor
struct SelectedLocationStoreTests {
    private let london = SavedLocation(
        id: 1, name: "London", countryCode: "GB", latitude: 51.5, longitude: -0.1, isPrimary: true
    )
    private let male = SavedLocation(
        id: 2, name: "Malé", countryCode: "MV", latitude: 4.17, longitude: 73.5
    )

    @Test("Before anything is selected it falls back to the favourite")
    func fallsBackToFavourite() {
        let store = SelectedLocationStore()
        // The launch case: opening straight onto METAR, before Home has published a selection.
        #expect(store.activeLocation(from: [male, london])?.id == london.id)
    }

    @Test("A selection wins over the favourite")
    func selectionWins() {
        let store = SelectedLocationStore()
        store.select(male.id)
        // Swipe Home to Malé, and METAR follows to Malé.
        #expect(store.activeLocation(from: [london, male])?.id == male.id)
    }

    @Test("A selection that no longer exists falls back rather than showing nothing")
    func staleSelectionFallsBack() {
        let store = SelectedLocationStore()
        store.select(male.id)
        // Malé has been deleted on the Locations tab while METAR was showing it.
        #expect(store.activeLocation(from: [london])?.id == london.id)
    }

    @Test("With no places saved there is nothing to show")
    func noPlaces() {
        #expect(SelectedLocationStore().activeLocation(from: []) == nil)
    }

    @Test("With no favourite it takes the first place")
    func noFavourite() {
        let store = SelectedLocationStore()
        var first = male
        first.isPrimary = false
        #expect(store.activeLocation(from: [first])?.id == first.id)
    }
}
