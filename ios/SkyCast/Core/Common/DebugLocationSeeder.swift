#if DEBUG

    import Foundation

    /// Seeds a couple of saved locations on first launch, **debug builds only**.
    ///
    /// A development convenience, so the data layer is exercisable in a running app without adding
    /// a place by hand.
    ///
    /// The whole file is inside `#if DEBUG`, so it is **absent from a release binary** rather than
    /// merely unreachable. It writes only when no locations exist, so it cannot duplicate rows or
    /// overwrite a place the user added, and is safe to call on every launch.
    enum DebugLocationSeeder {
        /// Inserts ``sampleLocations`` if, and only if, no locations exist yet.
        ///
        /// Errors are swallowed: a seeding problem must never stop the app starting, and the empty
        /// state it falls back to is a valid screen.
        static func seedIfEmpty(_ repository: any LocationRepository) async {
            do {
                guard try await repository.savedLocations().isEmpty else { return }
                for location in sampleLocations {
                    try await repository.save(location)
                }
            } catch {
                // Intentionally ignored, see the note above.
            }
        }

        /// Chosen for contrast rather than convenience: a mid-latitude maritime climate and an
        /// equatorial one, in different timezones and on opposite sides of the prime meridian.
        /// That exercises the location-timezone forecast grouping and the day/night symbol logic,
        /// which two nearby cities would not.
        ///
        /// The first entry becomes primary and is what the Home tab shows. Kept identical to
        /// Android's `DebugLocationSeeder.SAMPLE_LOCATIONS` so both platforms demonstrate and
        /// screenshot the same data.
        private static let sampleLocations: [LocationSearchResult] = [
            LocationSearchResult(
                name: "London",
                countryCode: "GB",
                state: "England",
                latitude: 51.5074,
                longitude: -0.1278
            ),
            LocationSearchResult(
                name: "Malé",
                countryCode: "MV",
                state: nil,
                latitude: 4.1748,
                longitude: 73.5089
            ),
        ]
    }

#endif
