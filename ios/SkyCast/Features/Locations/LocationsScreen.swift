import SwiftUI

/// The Locations tab, the user's saved places, reorderable and deletable.
///
/// Reads from `LocationRepository.savedLocations()`, which is already implemented and
/// SwiftData-backed, so this screen is pure presentation work. Both onward routes are wired
/// now so the navigation graph can be traversed end to end by the UI test and captured for
/// the README screenshots.
struct LocationsScreen: View {
    var body: some View {
        VStack(spacing: Spacing.md) {
            Image(systemName: "mappin.and.ellipse")
                .font(.system(size: 44))
                .foregroundStyle(.secondary)
                .accessibilityHidden(true)

            Text("Your saved places, reorderable, swipe to delete.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)

            NavigationLink("Add location") {
                AddLocationScreen()
            }
            .buttonStyle(.borderedProminent)
            .padding(.top, Spacing.sm)

            NavigationLink("Open a location (demo navigation)") {
                LocationDetailScreen(locationID: PreviewIdentifiers.locationID)
            }
            .buttonStyle(.bordered)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(Spacing.lg)
        .background(Color.skyBackground)
        .navigationTitle("Locations")
    }
}

/// Geocoding search, pushed from the Locations tab.
///
/// When built, debounce the query by ~400 ms before calling `LocationRepository.search()`,
/// the free API tier allows 60 calls/minute and a per-keystroke search would exhaust it in
/// seconds.
struct AddLocationScreen: View {
    var body: some View {
        PlaceholderScreen(
            title: "Add location",
            plannedContent: "Search for a city by name and save it."
        )
    }
}

/// Full conditions for one saved location, pushed from Today or Locations.
struct LocationDetailScreen: View {
    let locationID: Int64

    var body: some View {
        PlaceholderScreen(
            title: "Location details",
            plannedContent: "Full conditions for location #\(locationID)."
        )
    }
}

#Preview {
    NavigationStack { LocationsScreen() }
}
