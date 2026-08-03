import Foundation

/// Build-time configuration, read from the app's `Info.plist`.
///
/// The plist values are substituted from `Config/Secrets.xcconfig` (gitignored) at build
/// time, so no key is ever a source-code literal. See `ios/Config/Base.xcconfig`.
enum AppConfiguration {
    /// The OpenWeather API key, or an empty string when none was supplied.
    static let apiKey: String = string(for: "OpenWeatherAPIKey")

    static let baseURL: URL = {
        let raw = string(for: "OpenWeatherBaseURL")
        // The fallback keeps this non-optional: a missing plist entry is a build
        // misconfiguration, and the documented default is used instead of crashing at launch.
        return URL(string: raw) ?? URL(string: "https://api.openweathermap.org/")!
    }()

    /// False when no key was supplied at build time.
    ///
    /// The build still succeeds in that case, since CI has no secret, so the app checks this at
    /// runtime and shows setup instructions instead of failing every request with an opaque 401.
    static var isAPIKeyConfigured: Bool {
        // The placeholder from Secrets.xcconfig.example counts as unconfigured, so the user is
        // told to add a key rather than getting a 401.
        !apiKey.isEmpty && apiKey != "your_openweather_api_key_here"
    }

    static var isDebug: Bool {
        #if DEBUG
            true
        #else
            false
        #endif
    }

    private static func string(for key: String) -> String {
        guard let value = Bundle.main.object(forInfoDictionaryKey: key) as? String else {
            return ""
        }
        return value.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
