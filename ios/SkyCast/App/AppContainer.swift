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
    let metarRepository: any MetarRepository
    let spaceWeatherRepository: any SpaceWeatherRepository
    let locationRepository: any LocationRepository
    let settingsStore: SettingsStore
    /// Which place the non-Home tabs follow. See ``SelectedLocationStore``.
    let selectedLocationStore = SelectedLocationStore()

    private init(
        modelContainer: ModelContainer,
        weatherRepository: any WeatherRepository,
        metarRepository: any MetarRepository,
        spaceWeatherRepository: any SpaceWeatherRepository,
        locationRepository: any LocationRepository,
        settingsStore: SettingsStore
    ) {
        self.modelContainer = modelContainer
        self.weatherRepository = weatherRepository
        self.metarRepository = metarRepository
        self.spaceWeatherRepository = spaceWeatherRepository
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
            // If even an in-memory store cannot be built, the schema itself is invalid and
            // the process cannot function. fatalError rather than `try!` so the crash log
            // says *why* instead of just "unexpectedly found nil".
            modelContainer = Self.makeInMemoryContainer(context: "live() fallback")
        }

        let local = LocalDataStore(modelContainer: modelContainer)
        let api = OpenWeatherAPIClient()
        let monitor = Self.liveNetworkMonitor()

        return AppContainer(
            modelContainer: modelContainer,
            weatherRepository: WeatherRepositoryImpl(
                api: api,
                local: local,
                networkMonitor: monitor
            ),
            metarRepository: MetarRepositoryImpl(
                // Its own client: OpenWeatherAPIClient appends our key to every URL, and this
                // endpoint neither needs one nor should receive it. See AviationAPIClient.
                api: AviationAPIClient(),
                local: local,
                networkMonitor: monitor
            ),
            spaceWeatherRepository: SpaceWeatherRepositoryImpl(
                // Its own client too, for the same reason. See SpaceWeatherAPIClient.
                api: SpaceWeatherAPIClient(),
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
        let modelContainer = Self.makeInMemoryContainer(context: "preview()")
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
            metarRepository: MetarRepositoryImpl(
                api: AviationAPIClient(),
                local: local,
                networkMonitor: monitor
            ),
            spaceWeatherRepository: SpaceWeatherRepositoryImpl(
                api: SpaceWeatherAPIClient(),
                local: local,
                networkMonitor: monitor
            ),
            locationRepository: LocationRepositoryImpl(api: api, local: local),
            settingsStore: SettingsStore(defaults: defaults)
        )
    }

    /// The network monitor for the live graph.
    ///
    /// Honours a **debug-only** `-SkyCastForceOffline` launch argument, which reports the device as
    /// permanently offline. Two reasons it exists:
    ///
    /// 1. The iOS Simulator has no aeroplane mode, so the offline path, the single most valuable
    ///    thing to put in front of a user tester (see `docs/user-testing/task-scenarios.md`, T6),
    ///    is otherwise unreachable without unplugging the whole Mac.
    /// 2. It makes the offline and error screenshots in `docs/screenshots/` reproducible rather
    ///    than a matter of catching the app at the right moment.
    ///
    /// Compiled out of release builds entirely, so a shipped binary cannot be forced offline.
    private static func liveNetworkMonitor() -> any NetworkMonitoring {
        #if DEBUG
            if ProcessInfo.processInfo.arguments.contains("-SkyCastForceOffline") {
                return StaticNetworkMonitor(online: false)
            }
        #endif
        return NetworkMonitor()
    }

    /// Builds an in-memory container, or crashes with a message that identifies the cause.
    ///
    /// An in-memory `ModelContainer` can only fail if the `Schema` is invalid, a programmer
    /// error, not a runtime condition, and not something the app can recover from. A
    /// descriptive `fatalError` is therefore more useful than `try!`, which would produce a
    /// crash log with no explanation.
    private static func makeInMemoryContainer(context: String) -> ModelContainer {
        do {
            return try ModelContainerFactory.inMemory()
        } catch {
            fatalError("SkyCast schema is invalid (\(context)): \(error)")
        }
    }
}
