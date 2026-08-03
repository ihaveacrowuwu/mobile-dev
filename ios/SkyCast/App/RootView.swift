import SwiftUI

/// The app shell: four tabs, each owning an independent `NavigationStack`.
///
/// One stack **per tab** is the important detail. It is what makes each tab remember its
/// own scroll position and pushed screens across tab switches, the same property Android
/// achieves with `saveState`/`restoreState` in `SkyCastNavigator`. A single shared stack
/// would reset the user's place every time they visited Settings.
struct RootView: View {
    @State private var selectedTab: AppTab = .today

    var body: some View {
        TabView(selection: $selectedTab) {
            ForEach(AppTab.allCases) { tab in
                NavigationStack {
                    tab.destination
                }
                .tabItem {
                    Label(tab.title, systemImage: tab.systemImage)
                }
                // Stable identifiers for UI tests; the visible title is copy and may change.
                .accessibilityIdentifier(tab.accessibilityIdentifier)
                .tag(tab)
            }
        }
    }
}

/// The four top-level destinations.
enum AppTab: String, CaseIterable, Identifiable, Hashable {
    case today
    case forecast
    case locations
    case settings

    var id: String {
        rawValue
    }

    var title: String {
        switch self {
        case .today: "Today"
        case .forecast: "Forecast"
        case .locations: "Locations"
        case .settings: "Settings"
        }
    }

    var systemImage: String {
        switch self {
        case .today: "sun.max"
        case .forecast: "calendar"
        case .locations: "mappin.and.ellipse"
        case .settings: "gearshape"
        }
    }

    /// Used by `SkyCastUITests` to select a tab without depending on its label text.
    var accessibilityIdentifier: String {
        "tab_\(rawValue)"
    }

    @ViewBuilder
    var destination: some View {
        switch self {
        case .today: TodayScreen()
        case .forecast: ForecastScreen()
        case .locations: LocationsScreen()
        case .settings: SettingsScreen()
        }
    }
}

#Preview {
    RootView()
        .environment(AppContainer.preview())
        .environment(AppContainer.preview().settingsStore)
}
