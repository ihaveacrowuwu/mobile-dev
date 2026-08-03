import SwiftUI

/// The Forecast tab, five days, tappable through to a day detail.
///
/// The list is the next feature to build on top of `WeatherRepository.forecast(for:)`,
/// following the same stateful/stateless split as ``TodayScreen``. The link below exercises
/// the real push route in the meantime, so the navigation hierarchy is testable today.
struct ForecastScreen: View {
    var body: some View {
        PlaceholderScreen(
            title: "Forecast",
            plannedContent: "A five-day forecast list, tappable through to a 3-hourly breakdown.",
            linkTitle: "Open a day (demo navigation)"
        ) {
            DayDetailScreen(locationID: PreviewIdentifiers.locationID, date: Date())
        }
    }
}

/// The 3-hourly breakdown for one forecast day, pushed from the Forecast tab.
struct DayDetailScreen: View {
    let locationID: Int64
    let date: Date

    var body: some View {
        PlaceholderScreen(
            title: "Day details",
            plannedContent: "Hour-by-hour readings for "
                + date.formatted(date: .abbreviated, time: .omitted)
                + " at location #\(locationID)."
        )
    }
}

/// Stand-in route arguments used by the placeholder screens.
///
/// They exist only so the push destinations can be reached, and therefore screenshotted
/// and UI-tested, before the real lists that would supply genuine ids are built. Every
/// reference disappears as its screen is implemented.
enum PreviewIdentifiers {
    /// The first id `LocalDataStore` assigns, so it resolves to a real record once a
    /// location is saved.
    static let locationID: Int64 = 1
}

#Preview {
    NavigationStack { ForecastScreen() }
}
