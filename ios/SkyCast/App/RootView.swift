import SwiftUI

/// The app shell: four tabs, each owning an independent `NavigationStack`.
///
/// One stack **per tab** is the important detail. It is what makes each tab remember its
/// own scroll position and pushed screens across tab switches, the same property Android
/// achieves with `saveState`/`restoreState` in `SkyCastNavigator`. A single shared stack
/// would reset the user's place every time they visited Settings.
///
/// ## Liquid Glass
///
/// The tab bar, navigation bars and toolbars adopt Liquid Glass **automatically** because
/// the app is built against the iOS 26 SDK, there is no modifier to add. The one thing
/// worth opting into explicitly is `tabBarMinimizeBehavior`, below.
///
/// ## A note on UI testing
///
/// There is deliberately **no** `.accessibilityIdentifier` on the tabs. SwiftUI generates the
/// tab-bar buttons from each `Tab`'s label, and an identifier set on either the `Tab` or its
/// content does not reach the generated button, verified by dumping the accessibility tree.
/// `SkyCastUITests` therefore queries by label *scoped to* `app.tabBars`, which is
/// unambiguous even though "METAR" also appears as a screen heading.
///
/// (Android needs the opposite: `TopLevelDestination.testTag`, because Compose offers no
/// equivalent scoping and the bar label collides with the heading.)
struct RootView: View {
    @State private var selectedTab: AppTab = .home

    var body: some View {
        TabView(selection: $selectedTab) {
            ForEach(AppTab.allCases) { tab in
                Tab(tab.title, systemImage: tab.systemImage, value: tab) {
                    NavigationStack {
                        tab.destination
                    }
                }
            }
        }
        // Liquid Glass behaviour: the tab bar shrinks to a compact pill as the user
        // scrolls down, handing the full screen over to content and expanding again on
        // scroll up. This is the headline interaction of the new tab bar, and it is
        // opt-in rather than automatic.
        .tabBarMinimizeBehavior(.onScrollDown)
    }
}

/// The four top-level destinations.
enum AppTab: String, CaseIterable, Identifiable, Hashable {
    case home
    case metar
    case locations
    case settings

    var id: String {
        rawValue
    }

    var title: String {
        switch self {
        case .home: "Home"
        case .metar: "METAR"
        case .locations: "Locations"
        case .settings: "Settings"
        }
    }

    var systemImage: String {
        switch self {
        case .home: "house"
        case .metar: "airplane"
        case .locations: "mappin.and.ellipse"
        case .settings: "gearshape"
        }
    }

    @ViewBuilder
    var destination: some View {
        switch self {
        case .home: HomeScreen()
        case .metar: MetarScreen()
        case .locations: LocationsScreen()
        case .settings: SettingsScreen()
        }
    }
}

#Preview {
    let container = AppContainer.preview()
    return RootView()
        .environment(container)
        .environment(container.settingsStore)
}
