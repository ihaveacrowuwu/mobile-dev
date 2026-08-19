import Foundation
import Observation
import SwiftUI

/// Everything the Moon screen renders.
///
/// The phase, the distance and the upcoming phases are computed from the clock, so there is **no
/// error case, no offline case and no stale case**.
///
/// `location` is optional: only moonrise and moonset depend on where you are, so the screen still
/// renders before any place has been saved and simply hides one card.
struct MoonUiState: Equatable {
    var snapshot: MoonSnapshot?
    var location: SavedLocation?
    /// The place's own zone, so "moonrise 20:47" is 20:47 *there*. Falls back to the device's zone
    /// until the cached weather that carries the offset has been read.
    var timeZone: TimeZone = .current
    var isLoading = true

    /// True only in the moments before the first computation lands.
    var showsLoader: Bool {
        isLoading && snapshot == nil
    }

    /// Rise and set need a place. Everything else does not.
    var showsRiseAndSet: Bool {
        location != nil && snapshot?.moonrise != nil
    }
}

/// The Moon screen's state.
///
/// Recomputes on a timer as well as on appearance, since the illuminated fraction moves over an
/// evening.
@MainActor
@Observable
final class MoonViewModel {
    private(set) var state = MoonUiState()

    private let locationRepository: any LocationRepository
    private let weatherRepository: any WeatherRepository
    private let selectedLocationStore: SelectedLocationStore
    private let clock: () -> Date

    private var observationTask: Task<Void, Never>?
    private var tickTask: Task<Void, Never>?

    init(
        locationRepository: any LocationRepository,
        weatherRepository: any WeatherRepository,
        selectedLocationStore: SelectedLocationStore,
        clock: @escaping () -> Date = { .now }
    ) {
        self.locationRepository = locationRepository
        self.weatherRepository = weatherRepository
        self.selectedLocationStore = selectedLocationStore
        self.clock = clock
    }

    func start() {
        guard observationTask == nil else { return }
        // Computed immediately, before anything is read from disk: the sky does not depend on the
        // database, and a spinner in front of arithmetic would be theatre.
        recompute()
        observationTask = Task { await observe() }
        tickTask = Task { await tick() }
    }

    func stop() {
        observationTask?.cancel()
        observationTask = nil
        tickTask?.cancel()
        tickTask = nil
    }

    /// Re-reads the primary location, which may have changed on the Locations tab.
    func reload() async {
        stop()
        start()
    }

    private func observe() async {
        // The place selected on Home, not the favourite. See SelectedLocationStore.
        let locations = await (try? locationRepository.savedLocations()) ?? []
        guard let location = selectedLocationStore.activeLocation(from: locations) else {
            state.isLoading = false
            return
        }
        state.location = location
        recompute()

        // Read only for the location's UTC offset, which the cache satisfies without a request.
        // On failure the device's zone is used.
        for await dataState in weatherRepository.currentWeather(for: location) {
            if Task.isCancelled {
                return
            }
            if let weather = dataState.data {
                state.timeZone = weather.timeZone
                recompute()
            }
            state.isLoading = dataState.isLoading
        }
        state.isLoading = false
    }

    /// Recomputes every minute, which is the smallest quantity shown on screen.
    private func tick() async {
        while !Task.isCancelled {
            try? await Task.sleep(for: .seconds(60))
            if Task.isCancelled {
                return
            }
            recompute()
        }
    }

    private func recompute() {
        state.snapshot = MoonCalculator.snapshot(
            for: clock(),
            // Greenwich when no place is saved: the phase, the distance and the upcoming phases do
            // not depend on the observer at all, and the two figures that do are hidden in that case.
            latitude: state.location?.latitude ?? 0,
            longitude: state.location?.longitude ?? 0,
            timeZone: state.timeZone
        )
        state.isLoading = false
    }
}

/// The Moon tab.
struct MoonScreen: View {
    @Environment(AppContainer.self) private var container
    @State private var viewModel: MoonViewModel?

    var body: some View {
        Group {
            if let viewModel {
                MoonContent(state: viewModel.state)
            } else {
                LoadingView()
            }
        }
        .navigationTitle("Moon")
        .navigationBarTitleDisplayMode(.inline)
        // Dark for the whole screen, whichever appearance the phone is in, so the semantic colours
        // resolve against the night sky. The toolbar needs telling separately because it is not
        // inside this view's environment.
        .environment(\.colorScheme, .dark)
        .toolbarColorScheme(.dark, for: .navigationBar)
        .task {
            if viewModel == nil {
                viewModel = MoonViewModel(
                    locationRepository: container.locationRepository,
                    weatherRepository: container.weatherRepository,
                    selectedLocationStore: container.selectedLocationStore
                )
                viewModel?.start()
            } else {
                await viewModel?.reload()
            }
        }
        .onDisappear { viewModel?.stop() }
    }
}

/// The stateless half.
struct MoonContent: View {
    let state: MoonUiState

    var body: some View {
        if state.showsLoader {
            LoadingView(message: "Working out where the Moon is…")
        } else if let snapshot = state.snapshot {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.md) {
                    // Clear of the navigation bar, which the sky runs underneath.
                    MoonHero(snapshot: snapshot, timeZone: state.timeZone)
                        .padding(.top, Spacing.lg)

                    if state.showsRiseAndSet {
                        SectionHeading("Tonight")
                        MoonPathCard(snapshot: snapshot, timeZone: state.timeZone)
                    }

                    SectionHeading("Distance")
                    MoonDistanceCard(snapshot: snapshot)

                    SectionHeading("Coming up")
                    UpcomingPhasesCard(phases: snapshot.upcomingPhases, timeZone: state.timeZone)
                }
                .padding(Spacing.md)
            }
            // The sky ignores the safe area; the **content does not**.
            .background { Color.clear.nightSky().ignoresSafeArea() }
        }
    }
}

private struct SectionHeading: View {
    let title: String

    init(_ title: String) {
        self.title = title
    }

    var body: some View {
        Text(title)
            .font(.headline)
            .padding(.top, Spacing.xs)
            .accessibilityAddTraits(.isHeader)
    }
}

// The Moon, big, on the night sky, inside a ring showing where in the month it is.
#Preview("Waxing crescent") {
    NavigationStack {
        MoonContent(
            state: MoonUiState(
                snapshot: MoonCalculator.snapshot(
                    for: Date(timeIntervalSince1970: 1_787_090_400),
                    latitude: 51.5074,
                    longitude: -0.1278,
                    timeZone: .gmt
                ),
                location: SavedLocation(
                    id: 1,
                    name: "London",
                    countryCode: "GB",
                    state: "England",
                    latitude: 51.5074,
                    longitude: -0.1278,
                    sortOrder: 0,
                    isPrimary: true
                ),
                timeZone: .gmt,
                isLoading: false
            )
        )
        .navigationTitle("Moon")
    }
}
