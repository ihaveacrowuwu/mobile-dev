import Foundation
import Observation
import SwiftUI

/// Everything the METAR screen renders, one immutable value, one source of truth.
struct MetarUiState: Equatable {
    var location: SavedLocation?
    var report: MetarReport?
    var isLoading = false
    var isRefreshing = false
    var isStale = false
    var error: AppError?
    var hasNoLocation = false

    /// Blocking spinner **only** when there is genuinely nothing to render.
    var showsFullScreenLoader: Bool {
        isLoading && report == nil && !hasNoLocation
    }

    /// Blocking error **only** when no cached observation exists to fall back on.
    var showsFullScreenError: Bool {
        error != nil && report == nil && !hasNoLocation
    }

    var showsEmptyState: Bool {
        hasNoLocation && !isLoading
    }

    /// Non-blocking banner over an existing observation.
    var showsStaleBanner: Bool {
        report != nil && (error != nil || isStale)
    }

    var staleBannerMessage: String {
        error?.message ?? "This observation may be out of date."
    }
}

/// The METAR screen's state.
///
/// Follows the **primary** location rather than paging like Home does. A METAR belongs to an airport,
/// not to a town, and the nearest airport to two saved places is often the same one, paging between
/// places that show an identical observation would be motion without information.
@MainActor
@Observable
final class MetarViewModel {
    private(set) var state = MetarUiState(isLoading: true)

    private let metarRepository: any MetarRepository
    private let locationRepository: any LocationRepository
    private let selectedLocationStore: SelectedLocationStore

    private var observationTask: Task<Void, Never>?

    init(
        metarRepository: any MetarRepository,
        locationRepository: any LocationRepository,
        selectedLocationStore: SelectedLocationStore
    ) {
        self.metarRepository = metarRepository
        self.locationRepository = locationRepository
        self.selectedLocationStore = selectedLocationStore
    }

    func start() {
        guard observationTask == nil else { return }
        observationTask = Task { await observe() }
    }

    func stop() {
        observationTask?.cancel()
        observationTask = nil
    }

    /// Re-reads the primary location, which may have changed on the Locations tab.
    func reload() async {
        stop()
        start()
    }

    func refresh() async {
        guard let location = state.location else { return }
        state.isRefreshing = true
        let error = await metarRepository.refresh(location)
        state.isRefreshing = false
        state.error = error
        if error == nil {
            await observe()
        }
    }

    private func observe() async {
        do {
            // The place selected on Home, not the favourite: following the favourite meant swiping
            // Home to Malé and still being shown London's airport. See SelectedLocationStore.
            let locations = try await locationRepository.savedLocations()
            guard let location = selectedLocationStore.activeLocation(from: locations) else {
                state = MetarUiState(hasNoLocation: true)
                return
            }
            state.location = location
            state.hasNoLocation = false

            for await dataState in metarRepository.nearestMetar(for: location) {
                if Task.isCancelled {
                    return
                }
                // Keep the previous observation on failure: the visible half of the offline-first
                // promise, and the reason `DataState` carries data and error together.
                state.report = dataState.data ?? state.report
                state.isLoading = dataState.isLoading
                state.isRefreshing = dataState.isRefreshing
                state.isStale = dataState.isStale
                state.error = dataState.error
            }
        } catch {
            state.isLoading = false
            state.error = AppError.from(error)
        }
    }
}

/// The METAR tab: the nearest reporting airport's observation.
struct MetarScreen: View {
    @Environment(AppContainer.self) private var container
    @State private var viewModel: MetarViewModel?

    var body: some View {
        Group {
            if let viewModel {
                MetarContent(state: viewModel.state, onRefresh: { await viewModel.refresh() })
            } else {
                LoadingView()
            }
        }
        .navigationTitle("METAR")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            if viewModel == nil {
                viewModel = MetarViewModel(
                    metarRepository: container.metarRepository,
                    locationRepository: container.locationRepository,
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
struct MetarContent: View {
    let state: MetarUiState
    let onRefresh: () async -> Void

    var body: some View {
        if state.showsFullScreenLoader {
            LoadingView(message: "Finding the nearest reporting airport…")
        } else if state.showsEmptyState {
            EmptyStateView(
                title: "No locations yet",
                message: "Add a place and SkyCast will show the nearest airport's report.",
                systemImage: "mappin.and.ellipse"
            )
        } else if state.showsFullScreenError, let error = state.error {
            // "No airport reporting nearby" is a fact about the place, not a network problem, so
            // it gets its own wording.
            if case .notFound = error {
                EmptyStateView(
                    title: "No airport reporting nearby",
                    message: "METARs are issued by airports. There is no station reporting near this "
                        + "place, try one closer to an airport.",
                    systemImage: "airplane.departure"
                )
            } else {
                ErrorView(error: error, onRetry: { Task { await onRefresh() } })
            }
        } else if let report = state.report {
            reportView(report)
        }
    }

    private func reportView(_ report: MetarReport) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.md) {
                if state.showsStaleBanner {
                    StaleDataBanner(
                        message: state.staleBannerMessage,
                        onRetry: { Task { await onRefresh() } }
                    )
                }
                StationHeader(report: report)
                // The category first and large, because it is the one thing a pilot looks for before
                // anything else, it decides whether the flight can be made under visual rules at all.
                FlightCategoryHero(category: report.flightCategory)
                SkySection(report: report)
                WindSection(report: report)
                DerivedSection(report: report)
                RawReportCard(raw: report.raw)
                DecodedRows(report: report)
            }
            .padding(Spacing.md)
        }
        .background(Color.skyBackground)
        .refreshable { await onRefresh() }
    }
}

private struct StationHeader: View {
    let report: MetarReport

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xxs) {
            Text(report.stationID)
                .font(.largeTitle.weight(.semibold))
            Text(report.stationName)
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Text("\(Int(report.distanceKm.rounded())) km away · elevation \(report.elevationMetres) m")
                .font(.caption)
                .foregroundStyle(.secondary)
            Text("Observed \(Self.observedFormatter.string(from: report.observedAt)) · \(age) ago")
                .font(.caption)
                .foregroundStyle(.secondary)
                .padding(.top, Spacing.xxs)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }

    /// How old the observation is, which is what a pilot actually checks.
    private var age: String {
        let minutes = Int(report.age(now: .now) / 60)
        return minutes < 60 ? "\(minutes) min" : "\(minutes / 60)h \(minutes % 60)m"
    }

    /// METARs are issued in UTC, the "Z" in the report, so the time is shown in it.
    private static let observedFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm 'UTC'"
        formatter.timeZone = TimeZone(identifier: "UTC")
        return formatter
    }()
}

// The flight-rules category, large, with what it actually means.
//
// The colours are the conventional ones, green visual, blue marginal, amber instrument, violet low
// instrument, and they come from the weather palette rather than being invented here, so they are the same
// contrast-checked colours the rest of the app uses.
#Preview("Report") {
    NavigationStack {
        MetarContent(
            state: MetarUiState(
                report: MetarReport(
                    stationID: "EGLC",
                    stationName: "London City Arpt, EN, GB",
                    distanceKm: 12.7,
                    latitude: 51.505,
                    longitude: 0.055,
                    elevationMetres: 10,
                    observedAt: Date(timeIntervalSince1970: 1_787_062_800),
                    temperatureCelsius: 26,
                    dewPointCelsius: 14,
                    windDirectionDegrees: 270,
                    windSpeedKnots: 10,
                    visibilityStatuteMiles: 6,
                    visibilityIsOrGreater: true,
                    altimeterHectopascals: 1_010,
                    clouds: [],
                    flightCategory: .vfr,
                    raw: "METAR EGLC 181420Z AUTO 27010KT 240V300 9999 NCD 26/14 Q1010",
                    cachedAt: Date(timeIntervalSince1970: 1_787_063_100)
                )
            ),
            onRefresh: {}
        )
        .navigationTitle("METAR")
    }
}
