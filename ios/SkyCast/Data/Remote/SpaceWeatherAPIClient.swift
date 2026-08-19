import Foundation

/// One three-hour Kp period, as NOAA sends it.
///
/// ```json
/// {"time_tag":"2026-08-19T03:00:00","kp":5.00,"observed":"observed","noaa_scale":"G1"}
/// ```
///
/// Two things to know about the shape:
///
/// - `time_tag` has **no zone suffix**, and the values are UTC. Decoding it as a local time would shift every
///   reading by the device's offset, the same trap the OpenWeather DTOs document.
/// - `noaa_scale` is `null` far more often than not; it appears only at storm level.
struct KpForecastEntryDTO: Decodable, Sendable {
    let timeTag: String
    let kp: Double
    /// `observed`, `estimated` or `predicted`.
    let observed: String
    let noaaScale: String?

    enum CodingKeys: String, CodingKey {
        case timeTag = "time_tag"
        case kp
        case observed
        case noaaScale = "noaa_scale"
    }
}

/// NOAA's Space Weather Prediction Center.
protocol SpaceWeatherAPI: Sendable {
    /// The three-hourly planetary K index, observed and forecast.
    func kpForecast() async throws -> [KpForecastEntryDTO]
}

/// A third data source, and, like the aviation one, its own client.
///
/// ``APIClient`` appends `appid=<our key>` to every URL it builds and refuses to make a request without one, so
/// reusing it here would both fail when no key is configured and send our OpenWeather key to a host with no use
/// for it. That reasoning is identical to ``AviationAPIClient``'s, and both hosts are NOAA.
///
/// The `User-Agent` is deliberate: NOAA's usage policy asks callers to identify themselves so they can contact
/// whoever is generating traffic rather than blocking an anonymous source.
///
/// The Android counterpart is `SpaceWeatherApi` over `NetworkModule.provideNoaaOkHttpClient`.
struct SpaceWeatherAPIClient: SpaceWeatherAPI {
    private let session: URLSession
    private let baseURL: URL
    private let decoder: JSONDecoder

    init(
        session: URLSession = .shared,
        baseURL: URL = SpaceWeatherAPIClient.defaultBaseURL
    ) {
        self.session = session
        self.baseURL = baseURL
        decoder = JSONDecoder()
    }

    func kpForecast() async throws -> [KpForecastEntryDTO] {
        var request = URLRequest(url: baseURL.appendingPathComponent(Self.forecastPath))
        request.httpMethod = "GET"
        request.timeoutInterval = Self.timeoutSeconds
        request.setValue(Self.userAgent, forHTTPHeaderField: "User-Agent")

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch {
            // Translated here so nothing above the Data layer sees a URLError.
            throw AppError.from(error)
        }

        guard let http = response as? HTTPURLResponse else {
            throw AppError.unknown(description: "Non-HTTP response")
        }
        guard (200..<300).contains(http.statusCode) else {
            throw AppError.fromHTTPStatus(http.statusCode)
        }

        do {
            return try decoder.decode([KpForecastEntryDTO].self, from: data)
        } catch {
            throw AppError.decoding(detail: String(describing: error))
        }
    }

    static let defaultBaseURL = URL(string: "https://services.swpc.noaa.gov/")!

    private static let forecastPath = "products/noaa-planetary-k-index-forecast.json"
    private static let timeoutSeconds: TimeInterval = 20
    private static let userAgent = "SkyCast/1.0 (student coursework; github.com/nauhaan)"
}
