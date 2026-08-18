import SwiftUI

/// One labelled reading, e.g. "Humidity" / "69%".
///
/// `value` is an already-formatted display string: how many decimal places, which unit symbol and
/// what time format are presentation decisions, so they happen in the feature layer
/// (`Features/Common/WeatherDetails.swift`) and the design system just renders what it is given.
///
/// The Android counterpart is `core/designsystem/component/WeatherDetail.kt`.
struct WeatherDetail: Identifiable, Equatable {
    let label: String
    let value: String
    let kind: WeatherDetailKind
    /// How the reading draws itself. See ``MetricVisual`` for why this is not simply a fraction.
    var visual: MetricVisual = .plain

    var id: String {
        label
    }
}

/// Which reading a tile shows.
///
/// An enum rather than a colour or symbol passed in by the caller, so the design system owns how
/// each metric looks and a feature cannot give humidity two different tints on two screens.
enum WeatherDetailKind: CaseIterable {
    case humidity
    case wind
    case pressure
    case visibility
    case sunrise
    case sunset

    /// Reported all along and never shown until the tiles could draw it.
    case cloud

    // Derived rather than reported, see ``Weather/dewPointCelsius`` and
    // ``Weather/daylightDuration``. They share the hue of the reading they are closest to: dew
    // point is moisture, so it takes humidity's blue, and daylight takes the sunrise gold.
    case dewPoint
    case daylight

    var symbolName: String {
        switch self {
        case .humidity: "humidity.fill"
        case .wind: "wind"
        case .pressure: "barometer"
        case .visibility: "eye.fill"
        case .sunrise: "sunrise.fill"
        case .sunset: "sunset.fill"
        case .cloud: "cloud.fill"
        case .dewPoint: "thermometer.medium"
        case .daylight: "sun.horizon.fill"
        }
    }

    var accent: Color {
        switch self {
        case .humidity, .dewPoint: WeatherPalette.humidity
        case .wind: WeatherPalette.wind
        case .pressure: WeatherPalette.pressure
        case .visibility: WeatherPalette.visibility
        case .sunrise, .daylight: WeatherPalette.sunrise
        case .sunset: WeatherPalette.sunset
        case .cloud: WeatherPalette.onCloudContainer
        }
    }
}

/// The secondary readings: humidity, wind, pressure, visibility, sunrise, sunset.
///
/// A `LazyVGrid` with an **adaptive** column, so the tiles reflow instead of clipping when the user
/// turns Dynamic Type up. A fixed two-column grid breaks at accessibility sizes.
///
/// Each tile carries its metric's colour and, where the reading has a scale, an indicator showing
/// where on that scale it sits.
///
/// No glass: these tiles sit inside a page of content rather than floating over it.
struct WeatherDetailGrid: View {
    let details: [WeatherDetail]

    var body: some View {
        LazyVGrid(
            columns: [GridItem(.adaptive(minimum: minimumTileWidth), spacing: Spacing.sm)],
            spacing: Spacing.sm
        ) {
            ForEach(details) { detail in
                WeatherDetailTile(detail: detail)
            }
        }
    }

    /// Wide enough for "1009 hPa" at the default text size; the adaptive column then decides how
    /// many fit, dropping to one per row at accessibility sizes.
    private let minimumTileWidth: CGFloat = 150
}

private struct WeatherDetailTile: View {
    let detail: WeatherDetail

    /// The tint of the page this tile is on, if it has one.
    @Environment(\.weatherSurfaceTint) private var weatherSurfaceTint

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Label {
                Text(detail.label)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } icon: {
                Image(systemName: detail.kind.symbolName)
                    .font(.caption)
                    .foregroundStyle(detail.kind.accent)
            }

            HStack(alignment: .center, spacing: Spacing.sm) {
                Text(detail.value)
                    .font(.title3.weight(.medium))
                    .minimumScaleFactor(0.7)
                    .lineLimit(1)

                Spacer(minLength: 0)

                switch detail.visual {
                case .plain:
                    EmptyView()
                case let .gauge(fraction):
                    MetricGauge(fraction: fraction, colour: detail.kind.accent)
                case let .compass(degrees, _):
                    WindCompass(degrees: degrees, colour: detail.kind.accent)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Spacing.md)
        // The surface first, then a whisper of the page's mood over it. Layered rather than
        // blended into a single colour so the base stays the one the system guarantees contrast
        // against, the tile belongs to a warm page or a cold one without any text on it becoming
        // a contrast problem that has to be re-checked per condition.
        .background {
            RoundedRectangle(cornerRadius: Radius.md, style: .continuous)
                .fill(Color.skySurface)
                .overlay {
                    if let weatherSurfaceTint {
                        RoundedRectangle(cornerRadius: Radius.md, style: .continuous)
                            .fill(weatherSurfaceTint.opacity(tintOpacity))
                    }
                }
        }
        // One announcement per tile. Without this, VoiceOver reads "Humidity" and "69%" as two
        // unrelated fragments and the pairing is lost. The bar is decorative, it says the same
        // thing as the value.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(announcement)
    }

    /// The compass draws a bearing that the value alone does not mention, so it is spoken.
    private var announcement: String {
        if case let .compass(_, cardinal) = detail.visual {
            return "\(detail.label), \(detail.value) from the \(cardinal)"
        }
        return "\(detail.label), \(detail.value)"
    }

    /// Enough to be felt when swiping between a clear place and an overcast one, little enough
    /// that the tile still reads as a surface rather than a coloured chip.
    ///
    /// Higher than it was, and lighter for it: the previous 10% of the *mood* hue put a mid-dark
    /// slate over a white card, and the result read as dirty grey beside the near-white glass next
    /// to it. 45% of the container keeps a light card light and still carries the condition.
    private let tintOpacity: Double = 0.45
}

#Preview {
    ScrollView {
        WeatherDetailGrid(details: [
            WeatherDetail(label: "Humidity", value: "69%", kind: .humidity, visual: .gauge(0.69)),
            WeatherDetail(
                label: "Wind",
                value: "8.7 kt",
                kind: .wind,
                visual: .compass(degrees: 270, cardinal: "W")
            ),
            WeatherDetail(label: "Pressure", value: "29.92 inHg", kind: .pressure, visual: .gauge(0.45)),
            WeatherDetail(label: "Visibility", value: "5.4 NM", kind: .visibility, visual: .gauge(1)),
            WeatherDetail(label: "Cloud cover", value: "40%", kind: .cloud, visual: .gauge(0.4)),
            WeatherDetail(label: "Dew point", value: "12°C", kind: .dewPoint, visual: .gauge(0.6)),
        ])
        .padding(Spacing.md)
    }
    .background(Color.skyBackground)
}
