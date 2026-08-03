import Foundation

/// Everything SkyCast asks of OpenWeather.
///
/// A protocol so `WeatherRepositoryImpl` can be tested against a stub without stubbing
/// `URLSession` itself, mocking the HTTP layer tests URLSession, not our code.
protocol WeatherAPI: Sendable {
    func currentWeather(latitude: Double, longitude: Double) async throws -> CurrentWeatherDTO
    func forecast(latitude: Double, longitude: Double) async throws -> ForecastResponseDTO
    func searchLocations(query: String, limit: Int) async throws -> [GeocodingResultDTO]
}

/// `WeatherAPI` over `URLSession` with `async`/`await`.
///
/// `units=metric` is always requested so cached values are canonically Celsius and m/s;
/// converting to the user's preferred unit is then a pure function, which is why changing
/// units works offline.
///
/// The API key is appended centrally in `makeURL`, no call site can forget it, and it
/// appears in exactly one place in the codebase.
struct OpenWeatherAPIClient: WeatherAPI {
    private let session: URLSession
    private let baseURL: URL
    private let apiKey: String
    private let decoder: JSONDecoder

    init(
        baseURL: URL = AppConfiguration.baseURL,
        apiKey: String = AppConfiguration.apiKey,
        session: URLSession = .shared
    ) {
        self.baseURL = baseURL
        self.apiKey = apiKey
        self.session = session
        decoder = JSONDecoder()
    }

    func currentWeather(latitude: Double, longitude: Double) async throws -> CurrentWeatherDTO {
        try await get(
            path: "data/2.5/weather",
            query: [
                "lat": String(latitude),
                "lon": String(longitude),
                "units": Constants.metric,
                "lang": Constants.english,
            ]
        )
    }

    func forecast(latitude: Double, longitude: Double) async throws -> ForecastResponseDTO {
        try await get(
            path: "data/2.5/forecast",
            query: [
                "lat": String(latitude),
                "lon": String(longitude),
                "units": Constants.metric,
                "lang": Constants.english,
            ]
        )
    }

    func searchLocations(
        query: String,
        limit: Int = Constants.defaultSearchLimit
    ) async throws
        -> [GeocodingResultDTO]
    {
        try await get(
            path: "geo/1.0/direct",
            query: ["q": query, "limit": String(limit)]
        )
    }

    // MARK: - Internals

    private func get<Response: Decodable>(
        path: String,
        query: [String: String]
    ) async throws
        -> Response
    {
        // Fail fast and specifically: a missing key produces a clear configuration error
        // rather than an opaque 401 from the server.
        guard AppConfiguration.isAPIKeyConfigured else { throw AppError.unauthorized }

        let url = try makeURL(path: path, query: query)
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = Constants.timeoutSeconds
        // Prefer a fresh response but fall back to the URL cache when offline; our own
        // SwiftData cache is the real offline story, this is just belt and braces.
        request.cachePolicy = .useProtocolCachePolicy

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch {
            // Translate here so nothing above the Data layer sees a URLError.
            throw AppError.from(error)
        }

        guard let http = response as? HTTPURLResponse else {
            throw AppError.unknown(description: "Non-HTTP response")
        }
        guard (200..<300).contains(http.statusCode) else {
            throw AppError.fromHTTPStatus(http.statusCode)
        }

        do {
            return try decoder.decode(Response.self, from: data)
        } catch {
            throw AppError.decoding(detail: String(describing: error))
        }
    }

    private func makeURL(path: String, query: [String: String]) throws -> URL {
        guard var components = URLComponents(
            url: baseURL.appendingPathComponent(path),
            resolvingAgainstBaseURL: false
        ) else {
            throw AppError.unknown(description: "Could not build URL for \(path)")
        }

        // Sorted for determinism: it makes URLs stable in test assertions and logs.
        components.queryItems = query
            .sorted { $0.key < $1.key }
            .map { URLQueryItem(name: $0.key, value: $0.value) }
            + [URLQueryItem(name: "appid", value: apiKey)]

        guard let url = components.url else {
            throw AppError.unknown(description: "Could not build URL for \(path)")
        }
        return url
    }

    private enum Constants {
        static let metric = "metric"
        static let english = "en"
        static let defaultSearchLimit = 8
        /// Short enough that a stalled request surfaces as an error the user can retry.
        static let timeoutSeconds: TimeInterval = 20
    }
}
