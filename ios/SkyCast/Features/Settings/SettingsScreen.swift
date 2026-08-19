import SwiftUI

/// The Settings tab.
///
/// Changing a unit survives a relaunch, and every other screen re-renders from cache with no
/// network call.
///
/// A `Form` rather than a hand-built list, which supplies correct grouping, Dynamic Type, dark mode
/// and VoiceOver semantics.
struct SettingsScreen: View {
    @Environment(AppContainer.self) private var container
    @Environment(SettingsStore.self) private var settings

    @State private var isShowingClearCacheConfirmation = false

    var body: some View {
        Form {
            Section("Units") {
                // A Picker renders as a menu or radio group depending on context and announces
                // "2 of 5" to VoiceOver.
                //
                // Each row reads its options straight off the unit's own `allCases` and
                // `displayName`, so adding a unit needs no change here.
                UnitPicker(title: "Temperature", selection: temperatureUnit)
                UnitPicker(title: "Wind speed", selection: windSpeedUnit)
                UnitPicker(title: "Pressure", selection: pressureUnit)
                UnitPicker(title: "Visibility", selection: visibilityUnit)
            }

            Section("Appearance") {
                Picker("Theme", selection: themeMode) {
                    ForEach(ThemeMode.allCases) { mode in
                        Text(mode.displayName).tag(mode)
                    }
                }
            }

            Section {
                Button("Clear cached weather", role: .destructive) {
                    isShowingClearCacheConfirmation = true
                }
                NavigationLink("About & licences") { AboutScreen() }
            } header: {
                Text("Storage")
            } footer: {
                Text("Clearing the cache removes saved forecasts. Your locations are kept.")
            }
        }
        .navigationTitle("Settings")
        // Destructive and not obviously reversible, so it is confirmed rather than
        // performed on a single tap.
        .confirmationDialog(
            "Clear cached weather?",
            isPresented: $isShowingClearCacheConfirmation,
            titleVisibility: .visible
        ) {
            Button("Clear cache", role: .destructive) {
                Task { await container.weatherRepository.clearCache() }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Saved locations and settings are not affected.")
        }
    }

    // MARK: - Bindings
    // Each write goes straight through to UserDefaults and comes back via the observable
    // store, so there is no second copy of the state to drift out of sync.

    private var temperatureUnit: Binding<TemperatureUnit> {
        Binding(
            get: { settings.preferences.temperatureUnit },
            set: { settings.setTemperatureUnit($0) }
        )
    }

    private var windSpeedUnit: Binding<WindSpeedUnit> {
        Binding(
            get: { settings.preferences.windSpeedUnit },
            set: { settings.setWindSpeedUnit($0) }
        )
    }

    private var pressureUnit: Binding<PressureUnit> {
        Binding(
            get: { settings.preferences.pressureUnit },
            set: { settings.setPressureUnit($0) }
        )
    }

    private var visibilityUnit: Binding<VisibilityUnit> {
        Binding(
            get: { settings.preferences.visibilityUnit },
            set: { settings.setVisibilityUnit($0) }
        )
    }

    private var themeMode: Binding<ThemeMode> {
        Binding(
            get: { settings.preferences.themeMode },
            set: { settings.setThemeMode($0) }
        )
    }
}

/// A settings row for choosing one of a unit type's cases.
///
/// Generic over ``DisplayUnit`` so every unit list is rendered the same way and adding a case
/// cannot leave this screen out of step, the Android counterpart's hand-written mapping produced
/// exactly that compile error when Kelvin was added.
private struct UnitPicker<Unit: DisplayUnit>: View where Unit.AllCases: RandomAccessCollection {
    let title: String
    @Binding var selection: Unit

    var body: some View {
        Picker(title, selection: $selection) {
            ForEach(Unit.allCases) { unit in
                Text(unit.displayName).tag(unit)
            }
        }
    }
}

/// Attribution and dependency licences, see `docs/licensing.md` (MO4).
struct AboutScreen: View {
    var body: some View {
        List {
            Section("Data") {
                Text("Weather data provided by OpenWeather.")
                Link("openweathermap.org", destination: URL(string: "https://openweathermap.org")!)
                // NOAA's data is public domain and needs no attribution clause honoured. Credited
                // anyway: two of the app's three sources are theirs, and a screen that names only one
                // of them reads as though the other two came from nowhere.
                Text("Aviation observations and space weather from NOAA.")
                Link("aviationweather.gov", destination: URL(string: "https://aviationweather.gov")!)
                Link("swpc.noaa.gov", destination: URL(string: "https://www.swpc.noaa.gov")!)
            }
            Section("Licences") {
                // iOS has no third-party runtime dependencies. Anything added must be listed in
                // docs/licensing.md.
                Text("SkyCast for iOS uses no third-party runtime dependencies.")
                    .foregroundStyle(.secondary)
            }
            Section("Build") {
                LabeledContent("API key configured", value: AppConfiguration.isAPIKeyConfigured ? "Yes" : "No")
            }
        }
        .navigationTitle("About & licences")
    }
}

#Preview {
    let container = AppContainer.preview()
    return NavigationStack { SettingsScreen() }
        .environment(container)
        .environment(container.settingsStore)
}
