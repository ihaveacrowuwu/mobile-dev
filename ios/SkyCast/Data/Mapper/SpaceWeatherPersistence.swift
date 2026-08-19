import Foundation

/// The space-weather reading to and from SwiftData.
///
/// Split from ``SpaceWeatherMapper`` for the same reason ``MetarPersistenceMapper`` is split from
/// ``MetarMapper``: one file turns a wire format into the domain, the other turns the domain into storage, and
/// they change for different reasons.
enum SpaceWeatherPersistence {
    static func persistent(from weather: SpaceWeather) -> PersistentSpaceWeather {
        PersistentSpaceWeather(
            kpNow: weather.kpNow,
            observedAt: weather.observedAt,
            stormLevel: weather.stormLevel,
            upcoming: weather.upcoming
                .map { period in
                    [
                        String(Int(period.time.timeIntervalSince1970)),
                        String(period.kp),
                        period.stormLevel ?? "",
                    ].joined(separator: fieldSeparator)
                }
                .joined(separator: periodSeparator),
            cachedAt: weather.cachedAt
        )
    }

    static func spaceWeather(from model: PersistentSpaceWeather) -> SpaceWeather {
        SpaceWeather(
            kpNow: model.kpNow,
            observedAt: model.observedAt,
            stormLevel: model.stormLevel,
            upcoming: model.upcoming
                .split(separator: Character(periodSeparator))
                .compactMap { encoded in
                    let fields = encoded.split(separator: Character(fieldSeparator), omittingEmptySubsequences: false)
                    guard let seconds = fields.first.flatMap({ Double($0) }),
                          fields.count > 1,
                          let kp = Double(fields[1])
                    else { return nil }
                    let storm = fields.count > 2 ? String(fields[2]) : ""
                    return KpPeriod(
                        time: Date(timeIntervalSince1970: seconds),
                        kp: kp,
                        // An empty field between separators has to come back as absent rather than as a storm
                        // called "".
                        stormLevel: storm.isEmpty ? nil : storm
                    )
                },
            cachedAt: model.cachedAt
        )
    }

    private static let periodSeparator = ";"
    private static let fieldSeparator = ":"
}
