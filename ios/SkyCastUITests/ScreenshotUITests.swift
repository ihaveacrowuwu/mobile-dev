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
        // Away and back before capturing. On a *first* launch the seeder inserts the second place
        // after Home has already read the list, SwiftData has no live observation, so Home
        // re-reads when the tab reappears, exactly as it does after a user adds a place. Without
        // this the capture shows a one-page Home and no page indicator, which is a fresh-install
        // artefact rather than what the screen looks like in use.
        selectTab(.locations)
        selectTab(.home)
        settle(seconds: 2)
        try capture("01-home")

        selectTab(.metar)
        // METAR fetches when its tab appears, and it is a network round trip to a second service. Without
        // waiting, this captured the "Finding the nearest reporting airport…" spinner, a screenshot that
        // succeeds while showing none of the screen it is named after.
        settle(seconds: 8)
        try capture("02-metar")

        selectTab(.moon)
        // The phase drawing settles instantly, it is computed, not fetched, but the tab transition
        // itself needs a beat before the capture.
        settle(seconds: 2)
        try capture("11-moon")
        // The day-detail screen is now reached from a place's detail screen, captured below.

        selectTab(.locations)
        try capture("03-locations")
        tapFirstLocationRow()
        try capture("06-location-detail")
        // The day-detail screen hangs off this screen's day rows now that the Forecast tab is gone.
        // Well down the page: the identity block, the reading, the hourly strip and the trend chart
        // all come first, so the rows are below the fold.
        scrollDown()
        scrollDown()
        tapFirstDayRow()
        try capture("10-day-detail")
        back()
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

    /// The five tab-bar destinations, addressed by their label.
    ///
    /// Querying `app.tabBars.buttons[label]` waits for the button to exist and fails loudly if it
    /// never does. Coordinates remain below only for content the accessibility tree does not name
    /// uniquely, like a specific list row.
    private enum Tab: String {
        case home = "Home"
        case metar = "METAR"
        case moon = "Moon"
        case locations = "Locations"
        case settings = "Settings"
    }

    private func selectTab(_ tab: Tab) {
        let button = app.tabBars.buttons[tab.rawValue]
        // The assertion below turns a missed tab into a failure instead of a screenshot of the
        // wrong screen.
        XCTAssertTrue(
            button.waitForExistence(timeout: 10),
            "The \(tab.rawValue) tab never appeared, so this capture would have stored the previous screen"
        )
        button.tap()
        // The tab must actually be selected before anything is captured. Without this the Settings
        // capture stored the Locations screen for two runs.
        XCTAssertTrue(
            waitForSelection(of: button),
            "Tapping \(tab.rawValue) did not select it"
        )
    }

    /// Polls until the tab reports itself selected, or gives up.
    private func waitForSelection(of button: XCUIElement, timeout: TimeInterval = 5) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if button.isSelected {
                return true
            }
            usleep(100_000)
        }
        return button.isSelected
    }

    /// The first saved location, immediately below the navigation title.
    private func tapFirstLocationRow() {
        tapNormalised(x: 0.5, y: 0.24)
    }

    /// The `+` in the navigation bar of the Locations tab.
    private func tapAddLocation() {
        tapNormalised(x: 0.93, y: 0.105)
    }

    /// Scrolls the current screen up by most of its height, for content below the fold.
    private func scrollDown() {
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.75))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.25))
        start.press(forDuration: 0.05, thenDragTo: end)
        settle(seconds: 1)
    }

    /// A day row in the detail screen's "Next days" list, once scrolled into view.
    ///
    /// Near the top: two scrolls put the list there. 0.62 was the first guess and landed in the gap
    /// between the list and the tiles below it, which produced a "day detail" capture that was
    /// really the detail screen, the same class of silent miss as the byte-identical shots earlier.
    private func tapFirstDayRow() {
        tapNormalised(x: 0.5, y: 0.15)
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
