import Foundation

/// One station's METAR, as `aviationweather.gov` returns it.
///
/// Two fields need custom decoding, because the API types them loosely:
///
/// - `visib` comes back as `6`, `3.5`, or the string `"6+"`, the last meaning "at least six
///   miles", which is how the format expresses a measurement beyond what the equipment reports.
/// - `wdir` is a bearing, except when the wind is variable, when it is the string `"VRB"`.
///
/// Declaring either as `Double` fails to parse a real response and declaring them as `String` fails
/// on the numeric case, so both go through ``LooseNumber``.
struct MetarDTO: Decodable, Sendable {
    let icaoID: String?
    let name: String?
    let latitude: Double?
    let longitude: Double?
    let elevationMetres: Int?
    /// Epoch seconds.
    let observedAtEpochSeconds: Double?
    let temperatureCelsius: Double?
    let dewPointCelsius: Double?
    let windDirection: LooseNumber?
    let windSpeedKnots: Int?
    let visibility: LooseNumber?
    /// Hectopascals, the `Q` group.
    let altimeterHectopascals: Double?
    let clouds: [CloudLayerDTO]?
    let flightCategory: String?
    let raw: String?

    enum CodingKeys: String, CodingKey {
        case icaoID = "icaoId"
        case name
        case latitude = "lat"
        case longitude = "lon"
        case elevationMetres = "elev"
        case observedAtEpochSeconds = "obsTime"
        case temperatureCelsius = "temp"
        case dewPointCelsius = "dewp"
        case windDirection = "wdir"
        case windSpeedKnots = "wspd"
        case visibility = "visib"
        case altimeterHectopascals = "altim"
        case clouds
        case flightCategory = "fltCat"
        case raw = "rawOb"
    }
}

struct CloudLayerDTO: Decodable, Sendable {
    let cover: String?
    /// Feet above the field. Absent for a clear sky.
    let baseFeet: Int?

    enum CodingKeys: String, CodingKey {
        case cover
        case baseFeet = "base"
    }
}

/// A JSON value that may arrive as a number or as a string.
///
/// Keeps both readings: `value` for the digits, and `text` so a caller can tell `"6+"` from `6`.
/// Decoding never throws for an unexpected shape, a field it cannot read becomes an empty value
/// rather than discarding the whole report, which is the right trade for one loosely typed field in
/// an otherwise usable observation.
struct LooseNumber: Decodable, Equatable, Sendable {
    let value: Double?
    let text: String?

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let number = try? container.decode(Double.self) {
            value = number
            text = nil
            return
        }
        if let string = try? container.decode(String.self) {
            text = string
            // "6+" keeps its digits; "VRB" has none, so it yields nil.
            value = Double(string.replacingOccurrences(of: "+", with: ""))
            return
        }
        value = nil
        text = nil
    }

    /// Whether the figure is a floor rather than a measurement.
    var isOrGreater: Bool {
        text?.hasSuffix("+") == true
    }
}
