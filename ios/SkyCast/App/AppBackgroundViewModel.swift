import Foundation
import Observation

/// The weather the whole app is painted with.
///
/// The favourite location supplies the background behind every screen that has no weather of its
/// own: METAR, Locations and Settings. Home paints its own background per page and draws over this
/// one, and the Moon tab has a night sky instead.
///
/// The Kotlin twin is `ui/AppBackgroundViewModel.kt`.
@MainActor
@Observable
final class AppBackgroundViewModel {
    private(set) var condition: WeatherCondition = .unknown
    private(set) var isDaytime = true

    private let locationRepository: any LocationRepository
    private let weatherRepository: any WeatherRepository

    private var observationTask: Task<Void, Never>?

    init(locationRepository: any LocationRepository, weatherRepository: any WeatherRepository) {
        self.locationRepository = locationRepository
        self.weatherRepository = weatherRepository
    }

    func start() {
        guard observationTask == nil else { return }
        observationTask = Task { await observe() }
    }

    /// Re-reads the favourite, which may have changed on the Locations tab.
    func reload() async {
        observationTask?.cancel()
        observationTask = Task { await observe() }
    }

    private func observe() async {
        guard let favourite = try? await locationRepository.primaryLocation() else { return }

        // Cached weather satisfies this without a request, Home is already fetching it, and a failure
        // needs no handling. The default is a neutral wash, not an error.
        for await dataState in weatherRepository.currentWeather(for: favourite) {
            if Task.isCancelled {
                return
            }
            guard let weather = dataState.data else { continue }
            condition = weather.condition
            isDaytime = weather.isDaytime
        }
    }
}
