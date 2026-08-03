import SwiftData
import SwiftUI

/// Application entry point.
///
/// The container is built once here and pushed into the environment, which is what lets
/// every view model receive its collaborators without a global singleton anywhere in the
/// codebase.
@main
struct SkyCastApp: App {
    @State private var container = AppContainer.live()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(container)
                .environment(container.settingsStore)
                // Debug-only, and a no-op unless the store is empty. Deleted with
                // DebugLocationSeeder once the Locations feature ships.
                .task {
                    #if DEBUG
                        await DebugLocationSeeder.seedIfEmpty(container.locationRepository)
                    #endif
                }
                // Applied at the root so the user's Light/Dark/System choice takes effect
                // everywhere, including sheets and alerts.
                .preferredColorScheme(container.settingsStore.preferences.themeMode.preferredColorScheme)
        }
        // Makes @Query available should a view ever need it, and keeps the app's single
        // ModelContainer consistent with the one LocalDataStore writes through.
        .modelContainer(container.modelContainer)
    }
}
