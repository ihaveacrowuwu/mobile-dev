import XCTest

/// End-to-end navigation test.
///
/// Drives the real app: every tab is reachable, push destinations open and dismiss, and back
/// returns to the right place.
///
/// XCUITest, not Swift Testing: UI automation still requires `XCTestCase`.
///
/// Run:
///     xcodebuild -scheme SkyCast -destination 'platform=iOS Simulator,name=iPhone 17' test
///
/// `@MainActor` because `XCUIApplication` is main-actor isolated. Under Swift 6 strict concurrency
/// a nonisolated stored property cannot have a main-actor-isolated default value.
@MainActor
final class NavigationFlowUITests: XCTestCase {
    // Non-optional: XCUIApplication() is cheap to construct and `launch()` in setUp is what
    // actually starts it. An implicitly unwrapped optional here would crash with a useless
    // message if setUp ever failed.
    private let app = XCUIApplication()

    override func setUpWithError() throws {
        continueAfterFailure = false
        app.launch()
    }

    /// A tab-bar button, located by label but **scoped to the tab bar**.
    ///
    /// Scoping is what makes the label safe: "Forecast" also appears as that screen's
    /// heading, but `app.tabBars` excludes it. An accessibility identifier would be more
    /// robust to copy changes, but SwiftUI does not propagate one onto the button it
    /// generates for a `Tab`, see the note in `RootView.swift`.
    private func tabButton(_ title: String) -> XCUIElement {
        app.tabBars.buttons[title]
    }

    /// A `Form` `Picker` row, matched on its title prefix.
    ///
    /// The accessibility label of such a row is "<title>, <current value>", so an exact-match
    /// query on the title alone never matches, and the full label is what lets us assert the
    /// selected value.
    private func pickerRow(titled title: String) -> XCUIElement {
        app.buttons
            .matching(NSPredicate(format: "label BEGINSWITH %@", title))
            .firstMatch
    }

    func testEveryTabIsReachable() {
        for title in ["Today", "Forecast", "Locations", "Settings"] {
            let tab = tabButton(title)
            XCTAssertTrue(tab.waitForExistence(timeout: 5), "Tab \(title) is missing")
            tab.tap()
        }

        // A section header exists only on the Settings screen itself, so finding one proves
        // the content rendered rather than merely that the tab is highlighted.
        XCTAssertTrue(app.staticTexts["Units"].waitForExistence(timeout: 5))
    }

    func testAddLocationPushesAndBackReturns() {
        tabButton("Locations").tap()

        let addButton = app.buttons["Add location"]
        XCTAssertTrue(addButton.waitForExistence(timeout: 5))
        addButton.tap()

        XCTAssertTrue(
            app.navigationBars["Add location"].waitForExistence(timeout: 5),
            "Add location screen did not appear"
        )

        // The system back button, so this asserts the real NavigationStack behaviour.
        app.navigationBars.buttons.element(boundBy: 0).tap()

        XCTAssertTrue(app.navigationBars["Locations"].waitForExistence(timeout: 5))
    }

    func testForecastPushesDayDetailAndBackReturns() {
        tabButton("Forecast").tap()

        let link = app.buttons["Open a day (demo navigation)"]
        XCTAssertTrue(link.waitForExistence(timeout: 5))
        link.tap()

        XCTAssertTrue(app.navigationBars["Day details"].waitForExistence(timeout: 5))

        app.navigationBars.buttons.element(boundBy: 0).tap()

        XCTAssertTrue(app.navigationBars["Forecast"].waitForExistence(timeout: 5))
    }

    func testChangingTemperatureUnitPersistsAcrossTabSwitches() {
        tabButton("Settings").tap()

        // A Form Picker row is exposed as a single button whose label is
        // "<title>, <current value>", e.g. "Temperature, Celsius (°C)". That is why this
        // matches on a prefix rather than the exact title, and it also gives us a precise
        // way to assert the *selected* value below.
        let picker = pickerRow(titled: "Temperature")
        XCTAssertTrue(picker.waitForExistence(timeout: 5), "Temperature picker row not found")
        XCTAssertEqual(picker.label, "Temperature, Celsius (°C)", "Expected Celsius by default")

        picker.tap()

        let fahrenheit = app.buttons["Fahrenheit (°F)"].firstMatch
        XCTAssertTrue(fahrenheit.waitForExistence(timeout: 5), "Picker menu did not present options")
        fahrenheit.tap()

        // The row's label now reflects the new selection.
        let updated = pickerRow(titled: "Temperature")
        XCTAssertTrue(
            updated.waitForExistence(timeout: 5),
            "Temperature row disappeared after selecting a unit"
        )
        XCTAssertEqual(updated.label, "Temperature, Fahrenheit (°F)")

        // Leave the tab and come back. Each tab owns its own NavigationStack, and the value
        // itself is restored from UserDefaults, this is the persistence proof.
        tabButton("Today").tap()
        tabButton("Settings").tap()

        let restored = pickerRow(titled: "Temperature")
        XCTAssertTrue(restored.waitForExistence(timeout: 5))
        XCTAssertEqual(
            restored.label,
            "Temperature, Fahrenheit (°F)",
            "The chosen unit did not survive a tab switch"
        )
    }

    func testAboutScreenIsReachableFromSettings() {
        tabButton("Settings").tap()

        let about = app.buttons["About & licences"]
        XCTAssertTrue(about.waitForExistence(timeout: 5))
        about.tap()

        // MO4: attribution and licences must be reachable in-app, not only in the repo.
        XCTAssertTrue(app.navigationBars["About & licences"].waitForExistence(timeout: 5))
    }

    /// Guards against the worst possible submission outcome: an app that fails to launch.
    func testAppLaunchesWithoutCrashing() {
        XCTAssertEqual(app.state, .runningForeground)
        XCTAssertTrue(app.tabBars.element.waitForExistence(timeout: 5))
    }
}
