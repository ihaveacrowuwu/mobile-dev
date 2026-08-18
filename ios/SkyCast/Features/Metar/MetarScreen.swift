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

    private var observationTask: Task<Void, Never>?

    init(metarRepository: any MetarRepository, locationRepository: any LocationRepository) {
        self.metarRepository = metarRepository
        self.locationRepository = locationRepository
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
            guard let location = try await locationRepository.primaryLocation() else {
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
                    locationRepository: container.locationRepository
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
                FlightCategoryBadge(category: report.flightCategory)
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

/// The flight-rules category, as a coloured badge.
///
/// The first thing a pilot looks for, so it is the first thing after the station. The colours come
/// from the weather palette rather than being invented here, so they are the same contrast-checked
/// colours the rest of the app uses.
private struct FlightCategoryBadge: View {
    let category: FlightCategory

    private var colour: Color {
        switch category {
        case .vfr: WeatherPalette.wind
        case .mvfr: WeatherPalette.humidity
        case .ifr: WeatherPalette.sunset
        case .lifr: WeatherPalette.pressure
        case .unknown: Color.secondary
        }
    }

    var body: some View {
        Text(category.label)
            .font(.headline)
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.xs)
            .background(colour.opacity(badgeOpacity), in: .rect(cornerRadius: Radius.sm))
            .accessibilityLabel("Flight category \(category.label)")
    }

    private let badgeOpacity: Double = 0.25
}

private struct RawReportCard: View {
    let raw: String

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text("Raw report")
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(raw)
                // Monospaced: a METAR is a fixed-format line, and the groups stay aligned.
                .font(.system(.body, design: .monospaced))
                .textSelection(.enabled)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Spacing.md)
        .background(Color.skySurface, in: .rect(cornerRadius: Radius.md))
    }
}

private struct DecodedRows: View {
    let report: MetarReport

    var body: some View {
        VStack(spacing: 0) {
            Text("Decoded")
                .font(.caption)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, Spacing.md)
                .padding(.top, Spacing.sm)

            ForEach(Array(rows.enumerated()), id: \.offset) { index, row in
                if index > 0 {
                    Divider().padding(.horizontal, Spacing.md)
                }
                HStack {
                    Text(row.label)
                        .foregroundStyle(.secondary)
                    Spacer()
                    Text(row.value)
                        .multilineTextAlignment(.trailing)
                }
                .padding(.horizontal, Spacing.md)
                .padding(.vertical, Spacing.sm)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(row.label), \(row.value)")
            }
        }
        .background(Color.skySurface, in: .rect(cornerRadius: Radius.md))
    }

    private var rows: [(label: String, value: String)] {
        var result: [(String, String)] = [
            ("Wind", windDescription),
            ("Visibility", visibilityDescription),
        ]
        if let temperature = report.temperatureCelsius {
            result.append(("Temperature", "\(Int(temperature.rounded()))°C"))
        }
        if let dewPoint = report.dewPointCelsius {
            result.append(("Dew point", "\(Int(dewPoint.rounded()))°C"))
        }
        if let altimeter = report.altimeterHectopascals {
            let inches = String(format: "%.2f", altimeter / Self.hectopascalsPerInch)
            result.append(("Altimeter", "\(Int(altimeter.rounded())) hPa · \(inches) inHg"))
        }
        result.append(("Cloud", cloudDescription))
        return result
    }

    private var windDescription: String {
        guard let knots = report.windSpeedKnots, knots > 0 else { return "Calm" }
        guard let bearing = report.windDirectionDegrees else { return "Variable at \(knots) kt" }
        return "\(bearing)° at \(knots) kt"
    }

    private var visibilityDescription: String {
        guard let miles = report.visibilityStatuteMiles else { return "" }
        let text = miles == miles.rounded() ? "\(Int(miles))" : "\(miles)"
        return report.visibilityIsOrGreater ? "\(text)+ mi" : "\(text) mi"
    }

    private var cloudDescription: String {
        guard !report.clouds.isEmpty else { return "No cloud reported" }
        return report.clouds
            .map { layer in
                layer.baseFeet.map { "\(layer.cover) at \($0) ft" } ?? layer.cover
            }
            .joined(separator: ", ")
    }

    /// The same pressure in the unit the other half of the world's charts use.
    private static let hectopascalsPerInch = 33.8639
}

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
