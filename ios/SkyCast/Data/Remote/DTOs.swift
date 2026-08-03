import Foundation

// Verbatim shapes of the OpenWeather responses.
//
// DTOs mirror the wire format exactly, including OpenWeather's awkward names and
// optional fields, and **never leave the `Data` layer**. `WeatherMapper` converts them
// into domain models, so an API change touches this file and the mapper only.
//
// Every non-essential field has a default so an unexpectedly sparse response degrades
// instead of failing to decode.

// MARK: - Current weather

struct CurrentWeatherDTO: Decodable, Sendable {
    let coordinates: CoordinatesDTO
    let weather: [WeatherDescriptionDTO]
    let main: MainDTO
    let visibility: Int
    let wind: WindDTO
    let clouds: CloudsDTO
    let observedAtEpochSeconds: Int
    let system: SystemDTO
    let timezoneOffsetSeconds: Int
    let cityID: Int64
    let cityName: String

    enum CodingKeys: String, CodingKey {
        case coordinates = "coord"
        case weather
        case main
        case visibility
        case wind
        case clouds
        case observedAtEpochSeconds = "dt"
        case system = "sys"
        case timezoneOffsetSeconds = "timezone"
        case cityID = "id"
        case cityName = "name"
    }

    init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        coordinates = try container.decode(CoordinatesDTO.self, forKey: .coordinates)
        weather = try container.decodeIfPresent([WeatherDescriptionDTO].self, forKey: .weather) ?? []
        main = try container.decode(MainDTO.self, forKey: .main)
        visibility = try container.decodeIfPresent(Int.self, forKey: .visibility) ?? 0
        wind = try container.decodeIfPresent(WindDTO.self, forKey: .wind) ?? WindDTO()
        clouds = try container.decodeIfPresent(CloudsDTO.self, forKey: .clouds) ?? CloudsDTO()
        observedAtEpochSeconds = try container.decode(Int.self, forKey: .observedAtEpochSeconds)
        system = try container.decodeIfPresent(SystemDTO.self, forKey: .system) ?? SystemDTO()
        timezoneOffsetSeconds = try container.decodeIfPresent(Int.self, forKey: .timezoneOffsetSeconds) ?? 0
        cityID = try container.decodeIfPresent(Int64.self, forKey: .cityID) ?? 0
        cityName = try container.decodeIfPresent(String.self, forKey: .cityName) ?? ""
    }
}

struct CoordinatesDTO: Decodable, Sendable {
    let longitude: Double
    let latitude: Double

    enum CodingKeys: String, CodingKey {
        case longitude = "lon"
        case latitude = "lat"
    }
}

struct WeatherDescriptionDTO: Decodable, Sendable {
    let id: Int
    let group: String
    let description: String
    let icon: String

    enum CodingKeys: String, CodingKey {
        case id
        case group = "main"
        case description
        case icon
    }

    init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decodeIfPresent(Int.self, forKey: .id) ?? 0
        group = try container.decodeIfPresent(String.self, forKey: .group) ?? ""
        description = try container.decodeIfPresent(String.self, forKey: .description) ?? ""
        icon = try container.decodeIfPresent(String.self, forKey: .icon) ?? ""
    }
}

struct MainDTO: Decodable, Sendable {
    let temperature: Double
    let feelsLike: Double
    let temperatureMin: Double
    let temperatureMax: Double
    let pressure: Int
    let humidity: Int

    enum CodingKeys: String, CodingKey {
        case temperature = "temp"
        case feelsLike = "feels_like"
        case temperatureMin = "temp_min"
        case temperatureMax = "temp_max"
        case pressure
        case humidity
    }

    init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        temperature = try container.decode(Double.self, forKey: .temperature)
        feelsLike = try container.decodeIfPresent(Double.self, forKey: .feelsLike) ?? temperature
        temperatureMin = try container.decodeIfPresent(Double.self, forKey: .temperatureMin) ?? temperature
        temperatureMax = try container.decodeIfPresent(Double.self, forKey: .temperatureMax) ?? temperature
        pressure = try container.decodeIfPresent(Int.self, forKey: .pressure) ?? 0
        humidity = try container.decodeIfPresent(Int.self, forKey: .humidity) ?? 0
    }
}

struct WindDTO: Decodable, Sendable {
    var speed: Double = 0
    var degrees: Int = 0
    /// Absent in calm conditions, hence optional rather than defaulted.
    var gust: Double?

    enum CodingKeys: String, CodingKey {
        case speed
        case degrees = "deg"
        case gust
    }

    init() {}

    init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        speed = try container.decodeIfPresent(Double.self, forKey: .speed) ?? 0
        degrees = try container.decodeIfPresent(Int.self, forKey: .degrees) ?? 0
        gust = try container.decodeIfPresent(Double.self, forKey: .gust)
    }
}

struct CloudsDTO: Decodable, Sendable {
    var cloudinessPercent: Int = 0

    enum CodingKeys: String, CodingKey {
        case cloudinessPercent = "all"
    }

    init() {}

    init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        cloudinessPercent = try container.decodeIfPresent(Int.self, forKey: .cloudinessPercent) ?? 0
    }
}

struct SystemDTO: Decodable, Sendable {
    var country: String = ""
    var sunriseEpochSeconds: Int = 0
    var sunsetEpochSeconds: Int = 0

    enum CodingKeys: String, CodingKey {
        case country
        case sunriseEpochSeconds = "sunrise"
        case sunsetEpochSeconds = "sunset"
    }

    init() {}

    init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        country = try container.decodeIfPresent(String.self, forKey: .country) ?? ""
        sunriseEpochSeconds = try container.decodeIfPresent(Int.self, forKey: .sunriseEpochSeconds) ?? 0
        sunsetEpochSeconds = try container.decodeIfPresent(Int.self, forKey: .sunsetEpochSeconds) ?? 0
    }
}

// MARK: - Forecast

struct ForecastResponseDTO: Decodable, Sendable {
    let readings: [ForecastReadingDTO]
    let city: ForecastCityDTO

    enum CodingKeys: String, CodingKey {
        case readings = "list"
        case city
    }

    init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        readings = try container.decodeIfPresent([ForecastReadingDTO].self, forKey: .readings) ?? []
        city = try container.decode(ForecastCityDTO.self, forKey: .city)
    }
}

struct ForecastReadingDTO: Decodable, Sendable {
    let timeEpochSeconds: Int
    let main: MainDTO
    let weather: [WeatherDescriptionDTO]
    let wind: WindDTO
    /// Probability of precipitation, 0.0–1.0.
    let precipitationProbability: Double

    enum CodingKeys: String, CodingKey {
        case timeEpochSeconds = "dt"
        case main
        case weather
        case wind
        case precipitationProbability = "pop"
    }

    init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        timeEpochSeconds = try container.decode(Int.self, forKey: .timeEpochSeconds)
        main = try container.decode(MainDTO.self, forKey: .main)
        weather = try container.decodeIfPresent([WeatherDescriptionDTO].self, forKey: .weather) ?? []
        wind = try container.decodeIfPresent(WindDTO.self, forKey: .wind) ?? WindDTO()
        precipitationProbability = try container.decodeIfPresent(
            Double.self,
            forKey: .precipitationProbability
        ) ?? 0
    }
}

struct ForecastCityDTO: Decodable, Sendable {
    let id: Int64
    let name: String
    let country: String
    let timezoneOffsetSeconds: Int

    enum CodingKeys: String, CodingKey {
        case id
        case name
        case country
        case timezoneOffsetSeconds = "timezone"
    }

    init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decodeIfPresent(Int64.self, forKey: .id) ?? 0
        name = try container.decodeIfPresent(String.self, forKey: .name) ?? ""
        country = try container.decodeIfPresent(String.self, forKey: .country) ?? ""
        timezoneOffsetSeconds = try container.decodeIfPresent(Int.self, forKey: .timezoneOffsetSeconds) ?? 0
    }
}

// MARK: - Geocoding

/// One hit from `GET /geo/1.0/direct`.
struct GeocodingResultDTO: Decodable, Sendable {
    let name: String
    let latitude: Double
    let longitude: Double
    let country: String
    /// Present only for some countries, e.g. US states and UK constituent countries.
    let state: String?

    enum CodingKeys: String, CodingKey {
        case name
        case latitude = "lat"
        case longitude = "lon"
        case country
        case state
    }

    init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        name = try container.decode(String.self, forKey: .name)
        latitude = try container.decode(Double.self, forKey: .latitude)
        longitude = try container.decode(Double.self, forKey: .longitude)
        country = try container.decodeIfPresent(String.self, forKey: .country) ?? ""
        state = try container.decodeIfPresent(String.self, forKey: .state)
    }
}
