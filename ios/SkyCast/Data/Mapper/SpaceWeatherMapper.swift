import Foundation

/// NOAA's Kp feed to the domain, and to the store.
enum SpaceWeatherMapper {
    /// The current reading and the forecast ahead of it, from one feed.
    ///
    /// The feed mixes past and future in a single array and marks each entry `observed`, `estimated` or
    /// `predicted`. It is **not** trimmed to now, so the split has to come from that field rather than from
    /// comparing timestamps to the clock, the last "observed" entry is the present, and everything the feed calls
    /// estimated or predicted is the future even when its timestamp is a few minutes behind.
    ///
    /// Returns `nil` when the feed contains nothing measured at all, which would mean a shape change rather than
    /// a quiet day.
    static func spaceWeather(from entries: [KpForecastEntryDTO], cachedAt: Date) -> SpaceWeather? {
        let measured = entries.filter { $0.observed.lowercased() == observed }
        guard let latest = measured.max(by: { $0.timeTag < $1.timeTag }),
              let observedAt = date(from: latest.timeTag)
        else { return nil }

        let upcoming = entries
            .filter { $0.observed.lowercased() != observed }
            .compactMap { entry -> KpPeriod? in
                guard let time = date(from: entry.timeTag) else { return nil }
                return KpPeriod(time: time, kp: entry.kp, stormLevel: entry.noaaScale)
            }
            .sorted { $0.time < $1.time }

        return SpaceWeather(
            kpNow: latest.kp,
            observedAt: observedAt,
            stormLevel: latest.noaaScale,
            upcoming: upcoming,
            cachedAt: cachedAt
        )
    }

    /// NOAA's timestamps carry **no zone suffix** and are UTC.
    ///
    /// Parsed explicitly as UTC rather than as a local time: taking the device's zone here would shift every
    /// reading by the offset, which on this feed means attributing tonight's storm to this afternoon.
    private static func date(from timeTag: String) -> Date? {
        formatter.date(from: timeTag.replacingOccurrences(of: " ", with: "T"))
    }

    private static let formatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
        formatter.timeZone = TimeZone(identifier: "UTC")
        formatter.locale = Locale(identifier: "en_US_POSIX")
        return formatter
    }()

    private static let observed = "observed"
}
