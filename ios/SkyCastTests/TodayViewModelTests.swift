import Foundation
import Testing
@testable import SkyCast

/// ``TodayViewModel`` behaviour, with an emphasis on the **offline and error paths**.
///
/// The happy path is the easy half. The cases that matter most
/// are the tests asserting that a failed refresh never
/// blanks the screen and that stale cached data still renders.
///
/// `@MainActor` because the view model is main-actor isolated; the suite runs there too so
/// no `await` hop is needed to read its state.
@MainActor
@Suite("TodayViewModel")
struct TodayViewModelTests {
    private func makeViewModel(
        weather: FakeWeatherRepository,
        locations: FakeLocationRepository
    )
        -> TodayViewModel
    {
        TodayViewModel(
            weatherRepository: weather,
            locationRepository: locations,
            // A dedicated suite so tests never read or write the real app's settings.
            settingsStore: SettingsStore(
                defaults: UserDefaults(suiteName: "com.nauhaan.skycast.tests") ?? .standard
            )
        )
    }

    @Test("With no saved location the empty state shows, not an error")
    func emptyStateWhenNoLocation() async {
        let weather = FakeWeatherRepository()
        let locations = FakeLocationRepository() // no locations
        let viewModel = makeViewModel(weather: weather, locations: locations)

        viewModel.start()
        await viewModel.waitForIdle()

        #expect(viewModel.state.hasNoLocation)
        #expect(viewModel.state.showsEmptyState)
        #expect(!viewModel.state.showsFullScreenError)
        #expect(viewModel.state.error == nil)
    }

    @Test("Cached weather renders without a blocking loader")
    func cachedWeatherRendersImmediately() async {
        let weather = FakeWeatherRepository()
        weather.currentWeatherStates = [.success(Fixtures.weather())]
        let locations = FakeLocationRepository()
        locations.locations = [Fixtures.location()]

        let viewModel = makeViewModel(weather: weather, locations: locations)
        viewModel.start()
        await viewModel.waitForIdle()

        #expect(viewModel.state.showsContent)
        // Offline-first: a warm start shows no spinner.
        #expect(!viewModel.state.showsFullScreenLoader)
        #expect(viewModel.state.weather?.temperatureCelsius == 22)
    }

    @Test("A failed refresh keeps cached data and shows a banner, not an error screen")
    func failedRefreshPreservesCache() async {
        let weather = FakeWeatherRepository()
        weather.currentWeatherStates = [
            .success(Fixtures.weather(), stale: true),
            .failure(.offline, cached: Fixtures.weather(), stale: true),
        ]
        let locations = FakeLocationRepository()
        locations.locations = [Fixtures.location()]

        let viewModel = makeViewModel(weather: weather, locations: locations)
        viewModel.start()
        await viewModel.waitForIdle()

        // This is the assertion that protects the persistence marks: data survives a
        // network failure.
        #expect(viewModel.state.showsContent)
        #expect(viewModel.state.showsStaleBanner)
        #expect(!viewModel.state.showsFullScreenError)
        #expect(viewModel.state.error == .offline)
    }

    @Test("An error with no cache shows the full-screen error state")
    func errorWithoutCacheIsFullScreen() async {
        let weather = FakeWeatherRepository()
        weather.currentWeatherStates = [.loading(), .failure(.offline)]
        let locations = FakeLocationRepository()
        locations.locations = [Fixtures.location()]

        let viewModel = makeViewModel(weather: weather, locations: locations)
        viewModel.start()
        await viewModel.waitForIdle()

        #expect(!viewModel.state.showsContent)
        #expect(viewModel.state.showsFullScreenError)
    }

    @Test("Dismissing the banner hides it without discarding the data")
    func dismissingBannerKeepsData() async {
        let weather = FakeWeatherRepository()
        weather.currentWeatherStates = [.failure(.offline, cached: Fixtures.weather(), stale: true)]
        let locations = FakeLocationRepository()
        locations.locations = [Fixtures.location()]

        let viewModel = makeViewModel(weather: weather, locations: locations)
        viewModel.start()
        await viewModel.waitForIdle()
        #expect(viewModel.state.showsStaleBanner)

        viewModel.dismissBanner()

        #expect(!viewModel.state.showsStaleBanner)
        #expect(viewModel.state.showsContent)
    }

    @Test("Refresh delegates to the repository and re-arms the banner")
    func refreshDelegatesAndRearmsBanner() async {
        let weather = FakeWeatherRepository()
        weather.currentWeatherStates = [.success(Fixtures.weather())]
        let locations = FakeLocationRepository()
        locations.locations = [Fixtures.location()]

        let viewModel = makeViewModel(weather: weather, locations: locations)
        viewModel.start()
        await viewModel.waitForIdle()

        viewModel.dismissBanner()
        #expect(viewModel.state.isBannerDismissed)

        await viewModel.refresh()

        #expect(weather.refreshCallCount == 1)
        // A fresh attempt makes a dismissed banner relevant again, so the user is told if this
        // attempt failed too.
        #expect(!viewModel.state.isBannerDismissed)
    }

    @Test("Refresh is a no-op when there is no location to refresh")
    func refreshWithoutLocationDoesNothing() async {
        let weather = FakeWeatherRepository()
        let locations = FakeLocationRepository()
        let viewModel = makeViewModel(weather: weather, locations: locations)

        viewModel.start()
        await viewModel.waitForIdle()
        await viewModel.refresh()

        #expect(weather.refreshCallCount == 0)
    }

    @Test("A failed refresh surfaces the error without clearing the cache")
    func refreshFailureSurfacesError() async {
        let weather = FakeWeatherRepository()
        weather.currentWeatherStates = [.success(Fixtures.weather())]
        weather.refreshError = .rateLimited
        let locations = FakeLocationRepository()
        locations.locations = [Fixtures.location()]

        let viewModel = makeViewModel(weather: weather, locations: locations)
        viewModel.start()
        await viewModel.waitForIdle()

        await viewModel.refresh()

        #expect(viewModel.state.error == .rateLimited)
        #expect(viewModel.state.showsContent)
        #expect(!viewModel.state.isRefreshing)
    }
}

private extension TodayViewModel {
    /// Yields until the view model's start-up task has drained.
    ///
    /// `start()` launches an unstructured `Task`, so a test must give the runtime a chance
    /// to run it. Yielding a few times is enough because the fakes never actually suspend
    /// on I/O, and it beats an arbitrary `sleep`, which would make the suite slow *and*
    /// flaky.
    func waitForIdle(iterations: Int = 20) async {
        for _ in 0..<iterations {
            await Task.yield()
        }
    }
}
