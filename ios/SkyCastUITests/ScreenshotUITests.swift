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

    /// The five tab-reachable screens plus both pushed destinations, in README order.
    func testCaptureEveryScreen() throws {
        app.launch()

        try capture("01-today")

        tab("Forecast").tap()
        try capture("02-forecast")

        // The first forecast row, if the network supplied one. Guarded rather than asserted: this
        // test's job is to produce screenshots, and failing the whole run because a five-day
        // forecast has not arrived would be the wrong trade.
        let firstDay = app.buttons.containing(NSPredicate(format: "label CONTAINS 'high'")).firstMatch
        if firstDay.waitForExistence(timeout: 10) {
            firstDay.tap()
            try capture("10-day-detail")
            back()
        }

        tab("Locations").tap()
        try capture("03-locations")

        let londonRow = app.buttons["London, England, GB, shown on the Today tab"]
        if londonRow.waitForExistence(timeout: 5) {
            londonRow.tap()
            try capture("06-location-detail")
            back()
        }

        app.buttons["Add location"].tap()
        XCTAssertTrue(app.navigationBars["Add location"].waitForExistence(timeout: 5))
        try capture("04-add-location")
        back()

        tab("Settings").tap()
        try capture("05-settings")
    }

    /// The offline banner over cached content, and the full-screen error with no cache.
    ///
    /// Both are forced with the debug-only `-SkyCastForceOffline` launch argument. The Simulator has
    /// no aeroplane mode, so without it these two states could only be captured by unplugging the
    /// Mac at the right moment, see `AppContainer.liveNetworkMonitor()`.
    func testCaptureOfflineStates() throws {
        // First, a normal launch to warm the cache, so the banner has content to sit over.
        app.launch()
        XCTAssertTrue(tab("Today").waitForExistence(timeout: 10))
        // Give the initial fetch time to land before going offline.
        Thread.sleep(forTimeInterval: 6)
        app.terminate()

        app.launchArguments = ["-SkyCastForceOffline"]
        app.launch()
        XCTAssertTrue(tab("Today").waitForExistence(timeout: 10))
        // Pull to refresh so the failure is fresh rather than inherited.
        pullToRefresh()
        try capture("07-offline-banner")

        // Forecast for a location whose forecast was never cached shows the full-screen error
        // rather than a banner: there is nothing to put a banner over.
        tab("Forecast").tap()
        try capture("08-error-state")
    }

    /// Dark mode. The whole design is re-checked here, not merely tinted.
    func testCaptureDarkMode() throws {
        app.launch()
        // The simulator's appearance is set by the calling script; this capture just records
        // whatever it is currently in, and the script runs it twice.
        try capture("09-dark-mode")
    }

    // MARK: - Helpers

    /// A tab-bar button, located by label scoped to the tab bar, see `NavigationFlowUITests`.
    private func tab(_ title: String) -> XCUIElement {
        app.tabBars.buttons[title]
    }

    private func back() {
        app.navigationBars.buttons.element(boundBy: 0).tap()
    }

    private func pullToRefresh() {
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.35))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.85))
        start.press(forDuration: 0.1, thenDragTo: end)
    }

    private func capture(_ name: String) throws {
        let directory = outputDirectory
        // Let animations and any in-flight glass transition settle, so a capture never catches a
        // half-drawn frame.
        Thread.sleep(forTimeInterval: 1.5)

        let png = XCUIScreen.main.screenshot().pngRepresentation
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try png.write(to: directory.appendingPathComponent("\(name).png"))
    }
}
