import Foundation
import Observation

/// Which place the rest of the app is talking about, for this session.
///
/// The favourite means exactly two things:
///
/// 1. which Home page the app opens on at launch, and
/// 2. which place's weather colours the background on the other screens.
///
/// The other tabs follow **the page you are looking at on Home**, held here.
///
/// Session state rather than a stored preference, held on the container so three separately-created
/// view models share one value.
///
/// The Kotlin twin is `ui/common/SelectedLocationStore.kt`.
@MainActor
@Observable
final class SelectedLocationStore {
    /// `nil` until Home has resolved its first page.
    private(set) var selectedLocationID: Int64?

    func select(_ locationID: Int64) {
        selectedLocationID = locationID
    }

    /// The place the non-Home tabs should show, given everything saved.
    ///
    /// Falls back in this order:
    ///
    /// 1. the place selected on Home, when there is one;
    /// 2. the favourite, which covers launching straight onto METAR or Moon before Home has been on
    ///    screen to publish a selection;
    /// 3. the first saved place, so a database with no favourite still shows something;
    /// 4. `nil`, meaning nothing is saved yet.
    func activeLocation(from locations: [SavedLocation]) -> SavedLocation? {
        if let selectedLocationID, let selected = locations.first(where: { $0.id == selectedLocationID }) {
            return selected
        }
        return locations.first(where: \.isPrimary) ?? locations.first
    }
}
