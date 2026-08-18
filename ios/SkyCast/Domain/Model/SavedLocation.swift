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
    /// Exactly one saved location is primary, it is what the Home tab shows.
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
