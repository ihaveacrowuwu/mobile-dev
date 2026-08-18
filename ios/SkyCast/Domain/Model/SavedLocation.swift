import Foundation

/// A place the user has chosen to track.
///
/// User-owned data: never evicted by a cache policy, and it survives app restarts. This
/// is the core of the persistence requirement.
struct SavedLocation: Equatable, Sendable, Identifiable, Hashable {
    let id: Int64
    let name: String
    let countryCode: String
    let state: String?
    let latitude: Double
    let longitude: Double
    /// Position in the user's list; lets them reorder without touching other fields.
    var sortOrder: Int
    /// Exactly one saved location is the favourite.
    ///
    /// It does two things and no more: it decides which Home page the app opens on at launch, and it
    /// supplies the background colour the other screens are painted with. See
    /// ``SelectedLocationStore`` for what the other tabs follow.
    var isPrimary: Bool

    init(
        id: Int64,
        name: String,
        countryCode: String,
        state: String? = nil,
        latitude: Double,
        longitude: Double,
        sortOrder: Int = 0,
        isPrimary: Bool = false
    ) {
        self.id = id
        self.name = name
        self.countryCode = countryCode
        self.state = state
        self.latitude = latitude
        self.longitude = longitude
        self.sortOrder = sortOrder
        self.isPrimary = isPrimary
    }

    /// e.g. "London, England, GB", degrades gracefully when `state` is absent.
    var displayName: String {
        [name, state, countryCode].compactMap(\.self).joined(separator: ", ")
    }

    /// e.g. "London, GB", for tight spaces such as a navigation title.
    var shortDisplayName: String {
        "\(name), \(countryCode)"
    }

    /// How many places can be saved.
    ///
    /// A cap rather than no limit, for reasons about the app working rather than about storage: every
    /// saved place is a page on Home and a live weather subscription, and the free OpenWeather tier
    /// allows 60 calls a minute across the whole app. Ten places refreshing is comfortable; fifty would
    /// be a rate-limit error the user cannot diagnose. Ten is also about as many pages as anyone can
    /// usefully swipe through.
    static let maxSaved = 10

    /// Whether another place can be saved when `currentCount` are already stored.
    ///
    /// A function rather than each caller writing `count < maxSaved` itself. Two places need this
    /// decision, the repository, which enforces it, and the Locations screen, which hides the Add
    /// button, and a `<` in one with a `<=` in the other is a bug that shows up as a button leading
    /// only to an error. Here it is one comparison, tested once.
    static func canSaveAnother(currentCount: Int) -> Bool {
        currentCount < maxSaved
    }
}

/// A geocoding search hit, before the user commits to saving it.
struct LocationSearchResult: Equatable, Sendable, Identifiable {
    let name: String
    let countryCode: String
    let state: String?
    let latitude: Double
    let longitude: Double

    /// Coordinates identify a place uniquely; the API returns no id for search hits.
    var id: String {
        "\(latitude),\(longitude)"
    }

    var displayName: String {
        [name, state, countryCode].compactMap(\.self).joined(separator: ", ")
    }
}
