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
    /// Waits until the view model's start-up task has produced a settled state.
    ///
    /// `start()` launches an *unstructured* `Task`, and the cooperative scheduler makes no promise
    /// about how many yields it takes for that task to reach a given await point, so a fixed number
    /// of yields is not a synchronisation primitive.
    ///
    /// Waiting for **quiescence** is deterministic: the state must first move off its initial value
    /// (proving the task ran at all) and then stop changing for several consecutive yields (proving
    /// a multi-emission stream has drained, not merely produced its first element). It fails loudly
    /// rather than asserting against a half-initialised state.
    func waitForIdle() async {
        let initial = state
        var previous = state
        var stablePolls = 0

        for _ in 0..<maximumPolls {
            await Task.yield()

            if state != previous {
                previous = state
                stablePolls = 0
                continue
            }
            // Stability before the first change means nothing has been scheduled yet.
            guard state != initial else { continue }

            stablePolls += 1
            if stablePolls >= requiredStablePolls {
                return
            }
        }
        Issue.record("View model never settled after \(maximumPolls) yields; state: \(state)")
    }
}

/// Generous, because a too-low bound costs a flaky failure while a too-high one costs nothing,
/// the loop exits as soon as the state is quiescent, so a passing test never waits the full run.
private let maximumPolls = 2_000

/// Enough consecutive unchanged polls to distinguish "the stream has drained" from "the next
/// element has not been scheduled yet".
private let requiredStablePolls = 50
