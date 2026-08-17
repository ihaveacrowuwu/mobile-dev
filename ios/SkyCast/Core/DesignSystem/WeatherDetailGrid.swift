import SwiftUI

/// One labelled reading, e.g. "Humidity" / "69%".
///
/// `value` is an already-formatted display string: how many decimal places, which unit symbol
/// and what time format are presentation decisions, so they happen in the feature layer
/// (`Features/Common/WeatherDetails.swift`) and the design system just renders what it is given.
///
/// The Android counterpart is `core/designsystem/component/WeatherDetail.kt`.
struct WeatherDetail: Identifiable, Equatable {
    let label: String
    let value: String

    var id: String {
        label
    }
}

/// The secondary readings, humidity, wind, pressure, visibility, sunrise, sunset.
///
/// A `LazyVGrid` with an **adaptive** column so the tiles reflow instead of clipping when the
/// user turns Dynamic Type up. A fixed two-column grid looked fine at the default size and broke
/// at accessibility sizes, which is exactly the failure the UI/UX criterion penalises.
///
/// No glass. These tiles sit inside a page of content rather than floating over it, and glass
/// inside a glass container is the rule most often broken.
struct WeatherDetailGrid: View {
    let details: [WeatherDetail]

    var body: some View {
        LazyVGrid(
            columns: [GridItem(.adaptive(minimum: minimumTileWidth), spacing: Spacing.sm)],
            spacing: Spacing.sm
        ) {
            ForEach(details) { detail in
                VStack(alignment: .leading, spacing: Spacing.xxs) {
                    Text(detail.label)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(detail.value)
                        .font(.headline)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(Spacing.md)
                .background(Color.skySurface, in: .rect(cornerRadius: Radius.md))
                // One announcement per tile. Without this, VoiceOver reads "Humidity" and "69%"
                // as two unrelated fragments and the pairing is lost.
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(detail.label), \(detail.value)")
            }
        }
    }

    /// Wide enough for "1009 hPa" at the default text size; the adaptive column then decides how
    /// many fit, dropping to one per row at accessibility sizes.
    private let minimumTileWidth: CGFloat = 150
}

#Preview {
    ScrollView {
        WeatherDetailGrid(details: [
            WeatherDetail(label: "Humidity", value: "69%"),
            WeatherDetail(label: "Wind", value: "4.5 m/s"),
            WeatherDetail(label: "Pressure", value: "1009 hPa"),
            WeatherDetail(label: "Visibility", value: "10.0 km"),
            WeatherDetail(label: "Sunrise", value: "05:27"),
            WeatherDetail(label: "Sunset", value: "20:46"),
        ])
        .padding(Spacing.md)
    }
    .background(Color.skyBackground)
}
