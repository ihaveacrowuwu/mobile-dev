import Foundation
import SwiftData
import SwiftUI

/// The dependency graph, assembled once at launch.
///
/// A hand-written container rather than a DI framework, so iOS carries no third-party runtime
/// dependency. Android uses Hilt.
///
/// Injected through the SwiftUI environment, so any view can reach it and any test or preview can
/// substitute ``preview(networkAvailable:)``.
@MainActor
@Observable
final class AppContainer {
    let modelContainer: ModelContainer
    let weatherRepository: any WeatherRepository
    let locationRepository: any LocationRepository
    let settingsStore: SettingsStore

    private init(
        modelContainer: ModelContainer,
        weatherRepository: any WeatherRepository,
        locationRepository: any LocationRepository,
        settingsStore: SettingsStore
    ) {
        self.modelContainer = modelContainer
        self.weatherRepository = weatherRepository
        self.locationRepository = locationRepository
        self.settingsStore = settingsStore
    }

    /// The real graph: on-disk SwiftData, live network monitor, real API client.
    static func live() -> AppContainer {
        // A failure here means the on-disk store is unusable. Rather than crash on launch,
        // which would look like a broken app, fall back to an in-memory store so the
        // user can still browse and search. Persistence is degraded, not fatal.
        let modelContainer: ModelContainer
        do {
            modelContainer = try ModelContainerFactory.live()
        } catch {
            assertionFailure("Persistent store unavailable: \(error)")
            // If even in-memory fails the process cannot function at all.
            modelContainer = try! ModelContainerFactory.inMemory()
        }

        let local = LocalDataStore(modelContainer: modelContainer)
        let api = OpenWeatherAPIClient()
        let monitor = NetworkMonitor()

        return AppContainer(
            modelContainer: modelContainer,
            weatherRepository: WeatherRepositoryImpl(
                api: api,
                local: local,
                networkMonitor: monitor
            ),
            locationRepository: LocationRepositoryImpl(api: api, local: local),
            settingsStore: SettingsStore()
        )
    }

    /// An in-memory graph for SwiftUI previews and UI tests.
    ///
    /// Uses a per-instance `UserDefaults` suite so a preview cannot overwrite the real
    /// app's stored settings.
    static func preview(networkAvailable: Bool = true) -> AppContainer {
        let modelContainer = try! ModelContainerFactory.inMemory()
        let local = LocalDataStore(modelContainer: modelContainer)
        let api = OpenWeatherAPIClient()
        let monitor = StaticNetworkMonitor(online: networkAvailable)

        let defaults = UserDefaults(suiteName: "com.nauhaan.skycast.preview") ?? .standard

        return AppContainer(
            modelContainer: modelContainer,
            weatherRepository: WeatherRepositoryImpl(
                api: api,
                local: local,
                networkMonitor: monitor
            ),
            locationRepository: LocationRepositoryImpl(api: api, local: local),
            settingsStore: SettingsStore(defaults: defaults)
        )
    }
}
