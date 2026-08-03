import Foundation

/// Every conversion between wire/storage shapes and domain models.
///
/// These are **pure functions with no dependencies**, which makes them the cheapest and
/// highest-value tests in the project, see `WeatherMapperTests`. Keeping all mapping here
/// means an OpenWeather response change touches this file and nothing else.
enum WeatherMapper {
    // MARK: - Remote → Domain

    static func weather(
        from dto: CurrentWeatherDTO,
        locationID: Int64,
        locationName: String,
        cachedAt: Date
    )
        -> Weather
    {
        // OpenWeather always sends at least one entry, but tolerating its absence keeps a
        // malformed response from taking the app down.
        let primary = dto.weather.first

        return Weather(
            locationID: locationID,
            locationName: locationName.isEmpty ? dto.cityName : locationName,
            condition: WeatherCondition.fromOpenWeatherID(primary?.id ?? 0),
            description: (primary?.description ?? "").capitalizedFirstLetter,
            iconCode: primary?.icon ?? "",
            temperatureCelsius: dto.main.temperature,
            feelsLikeCelsius: dto.main.feelsLike,
            minTemperatureCelsius: dto.main.temperatureMin,
            maxTemperatureCelsius: dto.main.temperatureMax,
            humidityPercent: dto.main.humidity,
            pressureHpa: dto.main.pressure,
            windSpeedMetresPerSecond: dto.wind.speed,
            windDirectionDegrees: dto.wind.degrees,
            cloudinessPercent: dto.clouds.cloudinessPercent,
            visibilityMetres: dto.visibility,
            sunrise: Date(timeIntervalSince1970: TimeInterval(dto.system.sunriseEpochSeconds)),
            sunset: Date(timeIntervalSince1970: TimeInterval(dto.system.sunsetEpochSeconds)),
            observedAt: Date(timeIntervalSince1970: TimeInterval(dto.observedAtEpochSeconds)),
            cachedAt: cachedAt
        )
    }

    /// Groups the flat 3-hourly list into calendar days.
    ///
    /// Days are keyed in the **location's** timezone, not the device's: a forecast for
    /// Male' viewed from London must still be grouped by Maldivian days.
    static func forecast(
        from dto: ForecastResponseDTO,
        locationID: Int64,
        locationName: String,
        cachedAt: Date
    )
        -> Forecast
    {
        var calendar = Calendar(identifier: .gregorian)
        // `.gmt` rather than `TimeZone(secondsFromGMT: 0)!`, same value, no force unwrap.
        // A malformed offset from the API must degrade to GMT, not trap.
        calendar.timeZone = TimeZone(secondsFromGMT: dto.city.timezoneOffsetSeconds) ?? .gmt

        let readings = dto.readings.map { reading -> DatedReading in
            let time = Date(timeIntervalSince1970: TimeInterval(reading.timeEpochSeconds))
            let hourly = HourlyForecast(
                time: time,
                condition: WeatherCondition.fromOpenWeatherID(reading.weather.first?.id ?? 0),
                iconCode: reading.weather.first?.icon ?? "",
                temperatureCelsius: reading.main.temperature,
                precipitationProbability: reading.precipitationProbability,
                windSpeedMetresPerSecond: reading.wind.speed
            )
            return DatedReading(day: calendar.startOfDay(for: time), hourly: hourly, dto: reading)
        }

        let grouped = Dictionary(grouping: readings, by: \.day)

        let days: [ForecastDay] = grouped.keys.sorted().compactMap { day in
            guard let entries = grouped[day], !entries.isEmpty else { return nil }
            let representative = representativeEntry(from: entries, calendar: calendar)

            return ForecastDay(
                date: day,
                condition: representative.hourly.condition,
                description: (representative.dto.weather.first?.description ?? "").capitalizedFirstLetter,
                iconCode: representative.hourly.iconCode,
                minTemperatureCelsius: entries.map(\.dto.main.temperatureMin).min() ?? 0,
                maxTemperatureCelsius: entries.map(\.dto.main.temperatureMax).max() ?? 0,
                precipitationProbability: entries.map(\.hourly.precipitationProbability).max() ?? 0,
                hourly: entries.map(\.hourly).sorted { $0.time < $1.time }
            )
        }

        return Forecast(
            locationID: locationID,
            locationName: locationName.isEmpty ? dto.city.name : locationName,
            days: days,
            cachedAt: cachedAt
        )
    }

    static func searchResult(from dto: GeocodingResultDTO) -> LocationSearchResult {
        LocationSearchResult(
            name: dto.name,
            countryCode: dto.country,
            state: dto.state,
            latitude: dto.latitude,
            longitude: dto.longitude
        )
    }

    // MARK: - Domain → Local

    static func persistentWeather(from weather: Weather) -> PersistentWeather {
        PersistentWeather(
            locationID: weather.locationID,
            locationName: weather.locationName,
            conditionRawValue: weather.condition.rawValue,
            weatherDescription: weather.description,
            iconCode: weather.iconCode,
            temperatureCelsius: weather.temperatureCelsius,
            feelsLikeCelsius: weather.feelsLikeCelsius,
            minTemperatureCelsius: weather.minTemperatureCelsius,
            maxTemperatureCelsius: weather.maxTemperatureCelsius,
            humidityPercent: weather.humidityPercent,
            pressureHpa: weather.pressureHpa,
            windSpeedMetresPerSecond: weather.windSpeedMetresPerSecond,
            windDirectionDegrees: weather.windDirectionDegrees,
            cloudinessPercent: weather.cloudinessPercent,
            visibilityMetres: weather.visibilityMetres,
            sunrise: weather.sunrise,
            sunset: weather.sunset,
            observedAt: weather.observedAt,
            cachedAt: weather.cachedAt
        )
    }

    static func persistentReadings(from forecast: Forecast) -> [PersistentForecastReading] {
        forecast.days.flatMap { day in
            day.hourly.map { hour in
                PersistentForecastReading(
                    locationID: forecast.locationID,
                    locationName: forecast.locationName,
                    time: hour.time,
                    conditionRawValue: hour.condition.rawValue,
                    weatherDescription: day.description,
                    iconCode: hour.iconCode,
                    temperatureCelsius: hour.temperatureCelsius,
                    precipitationProbability: hour.precipitationProbability,
                    windSpeedMetresPerSecond: hour.windSpeedMetresPerSecond,
                    cachedAt: forecast.cachedAt
                )
            }
        }
    }

    static func persistentLocation(from location: SavedLocation) -> PersistentSavedLocation {
        PersistentSavedLocation(
            id: location.id,
            name: location.name,
            countryCode: location.countryCode,
            state: location.state,
            latitude: location.latitude,
            longitude: location.longitude,
            sortOrder: location.sortOrder,
            isPrimary: location.isPrimary
        )
    }

    // MARK: - Local → Domain

    static func weather(from model: PersistentWeather) -> Weather {
        Weather(
            locationID: model.locationID,
            locationName: model.locationName,
            condition: WeatherCondition(rawValue: model.conditionRawValue) ?? .unknown,
            description: model.weatherDescription,
            iconCode: model.iconCode,
            temperatureCelsius: model.temperatureCelsius,
            feelsLikeCelsius: model.feelsLikeCelsius,
            minTemperatureCelsius: model.minTemperatureCelsius,
            maxTemperatureCelsius: model.maxTemperatureCelsius,
            humidityPercent: model.humidityPercent,
            pressureHpa: model.pressureHpa,
            windSpeedMetresPerSecond: model.windSpeedMetresPerSecond,
            windDirectionDegrees: model.windDirectionDegrees,
            cloudinessPercent: model.cloudinessPercent,
            visibilityMetres: model.visibilityMetres,
            sunrise: model.sunrise,
            sunset: model.sunset,
            observedAt: model.observedAt,
            cachedAt: model.cachedAt
        )
    }

    /// Rebuilds the day grouping from cached records, in the device's timezone.
    static func forecast(from readings: [PersistentForecastReading]) -> Forecast? {
        guard let first = readings.first else { return nil }

        let calendar = Calendar.current
        let grouped = Dictionary(grouping: readings) { calendar.startOfDay(for: $0.time) }

        let days: [ForecastDay] = grouped.keys.sorted().compactMap { day in
            guard let entries = grouped[day], !entries.isEmpty else { return nil }

            let representative = entries.min { lhs, rhs in
                let lhsDistance = abs(calendar.component(.hour, from: lhs.time) - Constants.middayHour)
                let rhsDistance = abs(calendar.component(.hour, from: rhs.time) - Constants.middayHour)
                return lhsDistance < rhsDistance
            } ?? entries[0]

            return ForecastDay(
                date: day,
                condition: WeatherCondition(rawValue: representative.conditionRawValue) ?? .unknown,
                description: representative.weatherDescription,
                iconCode: representative.iconCode,
                minTemperatureCelsius: entries.map(\.temperatureCelsius).min() ?? 0,
                maxTemperatureCelsius: entries.map(\.temperatureCelsius).max() ?? 0,
                precipitationProbability: entries.map(\.precipitationProbability).max() ?? 0,
                hourly: entries
                    .sorted { $0.time < $1.time }
                    .map { reading in
                        HourlyForecast(
                            time: reading.time,
                            condition: WeatherCondition(rawValue: reading.conditionRawValue) ?? .unknown,
                            iconCode: reading.iconCode,
                            temperatureCelsius: reading.temperatureCelsius,
                            precipitationProbability: reading.precipitationProbability,
                            windSpeedMetresPerSecond: reading.windSpeedMetresPerSecond
                        )
                    }
            )
        }

        return Forecast(
            locationID: first.locationID,
            locationName: first.locationName,
            days: days,
            cachedAt: first.cachedAt
        )
    }

    static func location(from model: PersistentSavedLocation) -> SavedLocation {
        SavedLocation(
            id: model.id,
            name: model.name,
            countryCode: model.countryCode,
            state: model.state,
            latitude: model.latitude,
            longitude: model.longitude,
            sortOrder: model.sortOrder,
            isPrimary: model.isPrimary
        )
    }

    // MARK: - Internals

    /// The reading nearest local noon best characterises a whole day; the 03:00 reading
    /// would make every day look clear and cold.
    private static func representativeEntry(
        from entries: [DatedReading],
        calendar: Calendar
    )
        -> DatedReading
    {
        entries.min { lhs, rhs in
            let lhsDistance = abs(calendar.component(.hour, from: lhs.hourly.time) - Constants.middayHour)
            let rhsDistance = abs(calendar.component(.hour, from: rhs.hourly.time) - Constants.middayHour)
            return lhsDistance < rhsDistance
        } ?? entries[0]
    }

    /// A forecast reading paired with the calendar day it falls in, in the *location's*
    /// timezone.
    ///
    /// A named type rather than a tuple: a three-member tuple is at the limit of what stays
    /// readable, and `entries.map(\.dto.main.temperatureMin)` is far clearer than `.2.main…`.
    private struct DatedReading {
        let day: Date
        let hourly: HourlyForecast
        let dto: ForecastReadingDTO
    }

    private enum Constants {
        static let middayHour = 12
    }
}

private extension String {
    /// OpenWeather returns lowercase descriptions ("light rain"); we display them
    /// sentence-cased without touching the rest of the string.
    var capitalizedFirstLetter: String {
        guard let first else { return self }
        return first.uppercased() + dropFirst()
    }
}
