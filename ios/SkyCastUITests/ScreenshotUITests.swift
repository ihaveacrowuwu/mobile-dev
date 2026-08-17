import XCTest

/// Generates the README screenshots by driving the real app, so the same command regenerates all
/// of them after a UI change.
///
/// ## Not part of the normal test run
///
/// `scripts/test.sh` passes `-skip-testing:SkyCastUITests/ScreenshotUITests`, so the ordinary suite
/// does not write files into the repository. Run these explicitly:
///
/// ```
/// ./scripts/screenshots-ios.sh
/// ```
///
/// ## How the output path is found
///
/// From `#filePath`, the absolute path of *this file* on the machine that compiled it, walked up to
/// the repository root. Neither a shell variable nor xcodebuild's `TEST_RUNNER_` prefix reaches the
/// process running on the simulator, so an environment variable would leave the tests skipping
/// themselves in silence.
///
/// A simulator process is an ordinary macOS process, so it can write to a host path directly, which
/// avoids extracting `XCTAttachment`s from the result bundle and mapping their names onto the
/// README's expected paths.
@MainActor
final class ScreenshotUITests: XCTestCase {
    private let app = XCUIApplication()

    /// Where to write the PNGs.
    ///
    /// `SKYCAST_SCREENSHOT_DIR` wins when set; otherwise the path is derived from this file's own
    /// compile-time location, which is what makes the script work without any environment plumbing.
    private var outputDirectory: URL {
        if let path = ProcessInfo.processInfo.environment["SKYCAST_SCREENSHOT_DIR"] {
            return URL(fileURLWithPath: path, isDirectory: true)
        }
        // …/ios/SkyCastUITests/ScreenshotUITests.swift → …/docs/screenshots/ios
        return URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // SkyCastUITests
            .deletingLastPathComponent() // ios
            .deletingLastPathComponent() // repository root
            .appendingPathComponent("docs/screenshots/ios", isDirectory: true)
    }

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    // MARK: - Captures

    /// Every tab-reachable screen plus both pushed destinations, in README order.
    func testCaptureEveryScreen() throws {
        app.launch()
        // A clean install has no cache, so the hero would otherwise be captured mid-spinner.
        settle(seconds: 8)
        try capture("01-today")

        selectTab(.forecast)
        try capture("02-forecast")
        tapFirstForecastRow()
        try capture("10-day-detail")
        back()

        selectTab(.locations)
        try capture("03-locations")
        tapFirstLocationRow()
        try capture("06-location-detail")
        back()

        tapAddLocation()
        try capture("04-add-location")
        back()

        selectTab(.settings)
        try capture("05-settings")
    }

    /// The offline banner, sitting over content the cache still has.
    ///
    /// Forced with the debug-only `-SkyCastForceOffline` launch argument: the Simulator has no
    /// aeroplane mode, so this state would otherwise need the Mac unplugged at the right moment.
    /// See `AppContainer.liveNetworkMonitor()`.
    func testCaptureOfflineBannerOverCache() throws {
        // Online first, to warm the cache so the banner has something to sit over.
        app.launch()
        settle(seconds: 8)
        app.terminate()

        app.launchArguments = ["-SkyCastForceOffline"]
        app.launch()
        settle(seconds: 3)
        // Refresh so the failure is this run's, not one inherited from the previous launch.
        pullToRefresh()
        try capture("07-offline-banner")
    }

    /// The full-screen error, with no cache to fall back on.
    ///
    /// Must run against a **freshly installed** app, the calling script uninstalls first. With a
    /// warm cache the correct behaviour is the banner above, not an error screen, so capturing this
    /// after the other test produced a "08-error-state" that was really just the cached list.
    func testCaptureOfflineErrorFromColdStart() throws {
        app.launchArguments = ["-SkyCastForceOffline"]
        app.launch()
        settle(seconds: 5)
        try capture("08-error-state")
    }

    /// Dark mode. Appearance is set by the calling script, which runs this after switching.
    func testCaptureDarkMode() throws {
        app.launch()
        try capture("09-dark-mode")
    }

    // MARK: - Helpers

    /// The four tabs, as fractions across the tab bar.
    ///
    /// Coordinate taps rather than element queries. Locating the
    /// FAB, the first forecast row and the London row by label and predicate; when one of those
    /// queries failed to match, XCUITest neither failed nor progressed and the run sat on the first
    /// screen indefinitely. A normalised coordinate always resolves, so a wrong guess produces a
    /// visibly wrong screenshot, which is a diagnosable failure rather than a hang.
    private enum Tab: CGFloat {
        case today = 0.16
        case forecast = 0.38
        case locations = 0.62
        case settings = 0.85
    }

    private func selectTab(_ tab: Tab) {
        tapNormalised(x: tab.rawValue, y: 0.955)
    }

    /// The first saved location, immediately below the navigation title.
    private func tapFirstLocationRow() {
        tapNormalised(x: 0.5, y: 0.24)
    }

    /// The first forecast row.
    ///
    /// Deeper than the Locations row because the Forecast list carries a section header for the
    /// place name. Both were 0.24 at first, and the forecast tap landed on the card's top edge and
    /// navigated nowhere, which produced a "day detail" screenshot byte-identical to the forecast
    /// list. Comparing file sizes is what caught it.
    private func tapFirstForecastRow() {
        tapNormalised(x: 0.5, y: 0.31)
    }

    /// The `+` in the navigation bar of the Locations tab.
    private func tapAddLocation() {
        tapNormalised(x: 0.93, y: 0.105)
    }

    private func back() {
        // The leading navigation-bar button. Queried rather than tapped by coordinate because a
        // wrong tap here would silently leave the run one screen deep, poisoning every later shot.
        let backButton = app.navigationBars.buttons.element(boundBy: 0)
        if backButton.waitForExistence(timeout: 5) {
            backButton.tap()
        }
        settle(seconds: 1)
    }

    private func tapNormalised(x: CGFloat, y: CGFloat) {
        app.coordinate(withNormalizedOffset: CGVector(dx: x, dy: y)).tap()
        settle(seconds: 1)
    }

    private func pullToRefresh() {
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.35))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.85))
        start.press(forDuration: 0.1, thenDragTo: end)
        settle(seconds: 2)
    }

    private func settle(seconds: TimeInterval) {
        Thread.sleep(forTimeInterval: seconds)
    }

    private func capture(_ name: String) throws {
        let directory = outputDirectory
        // Let animations and any in-flight glass transition finish, so no capture catches a
        // half-drawn frame.
        settle(seconds: 1.5)

        let png = XCUIScreen.main.screenshot().pngRepresentation
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try png.write(to: directory.appendingPathComponent("\(name).png"))
    }
}
