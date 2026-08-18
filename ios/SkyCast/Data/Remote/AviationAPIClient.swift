import Foundation

/// The NOAA Aviation Weather Center endpoint SkyCast uses for METARs.
protocol AviationAPI: Sendable {
    /// Every station reporting inside a bounding box, as `minLat,minLon,maxLat,maxLon`.
    func metars(boundingBox: String) async throws -> [MetarDTO]
}

/// A second data source, and its own client.
///
/// The reason for both: OpenWeather has no METAR, the format is issued by airports and distributed
/// by national weather services, and this endpoint needs **no API key**. ``APIClient`` appends
/// `appid=<our key>` to every URL it builds and refuses to make a request without one, so reusing it
/// would both fail when no key is configured and send our key to a third-party host that has no use
/// for it.
///
/// The `User-Agent` is deliberate: NOAA's usage policy asks callers to identify themselves so they
/// can contact whoever is generating traffic rather than blocking an anonymous source.
///
/// The Android counterpart is `AviationWeatherApi` plus `NetworkModule.provideAviationOkHttpClient`.
struct AviationAPIClient: AviationAPI {
    private let session: URLSession
    private let baseURL: URL
    private let decoder: JSONDecoder

    init(
        session: URLSession = .shared,
        baseURL: URL = AviationAPIClient.defaultBaseURL
    ) {
        self.session = session
        self.baseURL = baseURL
        decoder = JSONDecoder()
    }

    func metars(boundingBox: String) async throws -> [MetarDTO] {
        guard var components = URLComponents(
            url: baseURL.appendingPathComponent("metar"),
            resolvingAgainstBaseURL: false
        ) else {
            throw AppError.unknown(description: "Could not build the METAR URL")
        }
        components.queryItems = [
            URLQueryItem(name: "bbox", value: boundingBox),
            URLQueryItem(name: "format", value: "json"),
        ]
        guard let url = components.url else {
            throw AppError.unknown(description: "Could not build the METAR URL")
        }

        var request = URLRequest(url: url)
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
            return try decoder.decode([MetarDTO].self, from: data)
        } catch {
            throw AppError.decoding(detail: String(describing: error))
        }
    }

    static let defaultBaseURL = URL(string: "https://aviationweather.gov/api/data/")!

    private static let timeoutSeconds: TimeInterval = 20
    private static let userAgent = "SkyCast/1.0 (student coursework; github.com/nauhaan)"
}
