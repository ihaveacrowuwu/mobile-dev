import SwiftUI

/// The Locations tab, the user's saved places.
///
/// A `List` with `.swipeActions`, not a custom row with buttons: swipe-to-delete is the iOS
/// convention for removing a list item, and `List` supplies the gesture, the animation and the
/// VoiceOver "Actions available" affordance for free. Android uses explicit icon buttons because
/// that is *its* convention.
struct LocationsScreen: View {
    @Environment(AppContainer.self) private var container
    @State private var viewModel: LocationsViewModel?

    var body: some View {
        Group {
            if let viewModel {
                LocationsContent(
                    state: viewModel.state,
                    onSetPrimary: { location in Task { await viewModel.setPrimary(location) } },
                    onDelete: { location in Task { await viewModel.delete(location) } }
                )
            } else {
                LoadingView()
            }
        }
        .navigationTitle("Locations")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                NavigationLink {
                    AddLocationScreen()
                } label: {
                    Label("Add location", systemImage: "plus")
                }
            }
        }
        .task {
            if viewModel == nil {
                viewModel = LocationsViewModel(locationRepository: container.locationRepository)
            }
            // Reload on every appearance: a place added on the search screen must show up here
            // when the user comes back.
            await viewModel?.load()
        }
    }
}

/// The stateless half.
struct LocationsContent: View {
    let state: LocationsUiState
    let onSetPrimary: (SavedLocation) -> Void
    let onDelete: (SavedLocation) -> Void

    var body: some View {
        if state.isLoading {
            LoadingView()
        } else if state.isEmpty {
            EmptyStateView(
                title: "No saved places",
                message: "Use the + button to search for a city and start tracking its weather.",
                systemImage: "mappin.and.ellipse"
            )
        } else {
            List {
                ForEach(state.locations) { location in
                    NavigationLink {
                        LocationDetailScreen(locationID: location.id)
                    } label: {
                        LocationRow(location: location)
                    }
                    .swipeActions(edge: .trailing) {
                        if state.canDelete {
                            Button("Remove \(location.name)", systemImage: "trash", role: .destructive) {
                                onDelete(location)
                            }
                        }
                    }
                    .swipeActions(edge: .leading) {
                        if !location.isPrimary {
                            Button("Show on Today", systemImage: "star") {
                                onSetPrimary(location)
                            }
                            .tint(.skyAccent)
                        }
                    }
                }
            }
        }
    }
}

private struct LocationRow: View {
    let location: SavedLocation

    var body: some View {
        HStack(spacing: Spacing.sm) {
            // Only the primary row shows a star; an empty slot would add visual noise for the
            // other rows without conveying anything.
            if location.isPrimary {
                Image(systemName: "star.fill")
                    .foregroundStyle(Color.skyAccent)
                    .accessibilityHidden(true)
            }
            VStack(alignment: .leading, spacing: Spacing.xxs) {
                Text(location.name)
                    .font(.body)
                Text(location.displayName)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        // One announcement per row, including whether it is the primary place, otherwise
        // VoiceOver reads a decorative star and two disconnected text fragments.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(
            location.isPrimary
                ? "\(location.displayName), shown on the Today tab"
                : location.displayName
        )
    }
}

#Preview("Populated") {
    NavigationStack {
        LocationsContent(
            state: LocationsUiState(
                locations: [
                    SavedLocation(
                        id: 1,
                        name: "London",
                        countryCode: "GB",
                        state: "England",
                        latitude: 51.5074,
                        longitude: -0.1278,
                        isPrimary: true
                    ),
                    SavedLocation(
                        id: 2,
                        name: "Malé",
                        countryCode: "MV",
                        latitude: 4.1748,
                        longitude: 73.5089
                    ),
                ],
                isLoading: false
            ),
            onSetPrimary: { _ in },
            onDelete: { _ in }
        )
        .navigationTitle("Locations")
    }
}

#Preview("Empty") {
    NavigationStack {
        LocationsContent(
            state: LocationsUiState(isLoading: false),
            onSetPrimary: { _ in },
            onDelete: { _ in }
        )
        .navigationTitle("Locations")
    }
}
