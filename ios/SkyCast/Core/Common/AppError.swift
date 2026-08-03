import Foundation

/// The **only** error type allowed to cross the repository boundary.
///
/// `URLError`, `DecodingError` and SwiftData errors are caught inside `Data` and
/// translated into one of these cases. View models therefore never import a networking
/// or persistence type, which is what keeps them testable without a simulator.
///
/// Mirrors `AppError.kt` on Android.
enum AppError: Error, Equatable, Sendable {
    /// No usable network connection at all.
    case offline
    /// The request was made but did not complete in time.
    case timeout
    /// The API accepted the request but has nothing for this location.
    case notFound
    /// Free-tier quota exceeded (HTTP 429). Back off and retry later.
    case rateLimited
    /// The API key is missing, invalid, or not yet activated (HTTP 401).
    ///
    /// Treated as a *configuration* problem, not a transient failure, the UI shows
    /// setup instructions rather than a Retry button.
    case unauthorized
    /// The service failed (HTTP 5xx). Retrying may work.
    case server(statusCode: Int)
    /// The response arrived but did not match the expected shape.
    case decoding(detail: String)
    /// Reading or writing the local store failed.
    case storage(detail: String)
    /// Anything we did not anticipate.
    case unknown(description: String)

    /// Whether offering the user a Retry action makes sense.
    ///
    /// `.unauthorized` is excluded, because retrying a bad API key can never succeed.
    var isRetryable: Bool {
        switch self {
        case .offline, .timeout, .rateLimited, .server, .unknown: true
        case .notFound, .unauthorized, .decoding, .storage: false
        }
    }

    /// True when the cause is the user's connectivity rather than our service.
    var isConnectivityRelated: Bool {
        switch self {
        case .offline, .timeout: true
        default: false
        }
    }

    /// Translates a framework error. Called only from the `Data` layer.
    ///
    /// `CancellationError` is rethrown untouched, structured concurrency requires that
    /// cancellation propagate rather than be reported as a failure.
    static func from(_ error: Error) -> AppError {
        if let appError = error as? AppError {
            return appError
        }

        if let urlError = error as? URLError {
            switch urlError.code {
            case .notConnectedToInternet, .dataNotAllowed, .networkConnectionLost:
                return .offline
            case .timedOut:
                return .timeout
            case .cannotFindHost, .cannotConnectToHost, .dnsLookupFailed:
                return .offline
            default:
                return .unknown(description: urlError.localizedDescription)
            }
        }

        if error is DecodingError {
            return .decoding(detail: String(describing: error))
        }

        return .unknown(description: error.localizedDescription)
    }

    /// Maps an HTTP status code to the matching case.
    static func fromHTTPStatus(_ code: Int) -> AppError {
        switch code {
        // OpenWeather returns 403 for a key that exists but lacks plan access.
        case 401, 403: .unauthorized
        case 404: .notFound
        case 429: .rateLimited
        default: .server(statusCode: code)
        }
    }
}

// MARK: - Presentation

/// User-facing copy for an error.
///
/// An extension in `Core` rather than part of the enum, so the mapping lives in one place and copy
/// cannot drift between screens.
extension AppError {
    var title: String {
        switch self {
        case .offline: "No internet connection"
        case .timeout: "That took too long"
        case .notFound: "Place not found"
        case .rateLimited: "Too many requests"
        case .unauthorized: "API key not configured"
        case .server: "Service unavailable"
        case .decoding: "Unexpected response"
        case .storage: "Storage problem"
        case .unknown: "Something went wrong"
        }
    }

    var message: String {
        switch self {
        case .offline:
            "Connect to a network and try again. Saved locations still work offline."
        case .timeout:
            "The weather service did not respond in time."
        case .notFound:
            "We couldn't find weather data for that location."
        case .rateLimited:
            "You've hit the free API limit. Try again in a minute."
        case .unauthorized:
            "Add your OpenWeather key to Config/Secrets.xcconfig and rebuild. "
                + "See the README for setup steps."
        case .server:
            "The weather service is having problems. Please try again shortly."
        case .decoding:
            "We couldn't read the weather data. This is a bug, please report it."
        case .storage:
            "Saved data couldn't be read. Clearing the cache in Settings may help."
        case .unknown:
            "An unexpected error occurred. Please try again."
        }
    }

    /// SF Symbol shown alongside the message.
    var symbolName: String {
        switch self {
        case .offline, .timeout: "wifi.slash"
        case .notFound: "mappin.slash"
        case .rateLimited: "hourglass"
        case .unauthorized: "key.slash"
        case .server: "exclamationmark.icloud"
        case .decoding, .storage: "exclamationmark.triangle"
        case .unknown: "exclamationmark.circle"
        }
    }
}
