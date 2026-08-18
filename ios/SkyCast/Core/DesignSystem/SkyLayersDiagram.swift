import SwiftUI

/// One cloud layer, ready to draw.
///
/// A presentation type rather than the domain's `CloudLayer`: the diagram needs a *fraction* of sky covered,
/// and turning "BKN" into three quarters is a decision about how to draw the word, not about what it means.
struct SkyLayer: Identifiable, Equatable {
    /// The coverage abbreviation as issued, FEW, SCT, BKN, OVC.
    let cover: String
    let baseFeet: Int
    /// How much of the width this layer's band fills, 0–1.
    let coverFraction: Double
    /// Broken and overcast layers form the ceiling; the lowest of them is marked.
    let isCeiling: Bool

    var id: String {
        "\(cover)-\(baseFeet)"
    }

    /// How much of a coverage abbreviation's sky is filled.
    static func coverFraction(for cover: String) -> Double {
        switch cover.uppercased() {
        case "FEW": 0.25
        case "SCT": 0.5
        case "BKN": 0.75
        case "OVC", "VV": 1
        default: 0.15
        }
    }
}

/// The sky above the field, drawn to scale, with the coded values beside it.
///
/// The vertical scale is real: a layer at 4800 ft sits roughly twice as high as one at 2400 ft, so
/// two observations can be compared at a glance. The scale comes from the highest layer rather than
/// being fixed, so a 1200 ft overcast day does not draw as a band along the floor.
///
/// The Kotlin twin is `SkyLayersDiagram.kt`.
struct SkyLayersDiagram: View {
    let layers: [SkyLayer]
    let announcement: String

    var body: some View {
        Canvas { context, size in
            let topFeet = Self.scaleTopFeet(for: layers)
            draw(axis: &context, size: size, topFeet: topFeet)
            for layer in layers {
                draw(layer: layer, into: &context, size: size, topFeet: topFeet)
            }
            // The ground, so the heights are heights *above the field* rather than above nothing.
            context.stroke(
                Path { $0.move(to: CGPoint(x: 0, y: size.height))
                    $0.addLine(to: CGPoint(x: size.width, y: size.height))
                },
                with: .color(.secondary),
                lineWidth: 2
            )
        }
        .frame(height: Self.height)
        .padding(Spacing.sm)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(announcement)
    }

    /// Height labels up the right-hand edge, so the vertical positions mean something.
    private func draw(axis context: inout GraphicsContext, size: CGSize, topFeet: Int) {
        var feet = Self.axisStepFeet
        while feet <= topFeet {
            let y = Self.y(forFeet: feet, topFeet: topFeet, height: size.height)
            context.stroke(
                Path { $0.move(to: CGPoint(x: 0, y: y))
                    $0.addLine(to: CGPoint(x: size.width, y: y))
                },
                with: .color(.primary.opacity(Self.axisOpacity)),
                lineWidth: 0.5
            )
            let label = context.resolve(
                Text("\(feet / 1_000)k").font(.caption2).foregroundStyle(.secondary)
            )
            let measured = label.measure(in: size)
            // Clamped into the box: the topmost gridline sits at the very top, and a label placed a
            // label-height above it draws off the edge of the card.
            context.draw(
                label,
                at: CGPoint(x: size.width, y: max(y - measured.height / 2, measured.height / 2)),
                anchor: .trailing
            )
            feet += Self.axisStepFeet
        }
    }

    /// One layer, as a band of cloud puffs at its height.
    ///
    /// The band's width carries the coverage, a FEW layer is a quarter of the sky, an OVC layer all of it,
    /// which is the same information the abbreviation carries, in a form that needs no glossary.
    private func draw(layer: SkyLayer, into context: inout GraphicsContext, size: CGSize, topFeet: Int) {
        let y = Self.y(forFeet: layer.baseFeet, topFeet: topFeet, height: size.height)
        let bandWidth = size.width * Self.cloudAreaFraction * layer.coverFraction
        let puffs = max(Int((layer.coverFraction * Double(Self.maximumPuffs)).rounded()), 1)
        let puffRadius = min(bandWidth / Double(puffs) / 2, Self.maximumPuffRadius)

        for index in 0..<puffs {
            let centre = CGPoint(x: puffRadius + Double(index) * (bandWidth / Double(puffs)), y: y)
            context.fill(
                Path(ellipseIn: CGRect(
                    x: centre.x - puffRadius,
                    y: centre.y - puffRadius,
                    width: puffRadius * 2,
                    height: puffRadius * 2
                )),
                with: .color(.secondary.opacity(Self.cloudOpacity))
            )
        }

        guard layer.isCeiling else { return }
        // The ceiling gets a dashed rule and a label, because it is the one height that decides whether a
        // flight can be made under visual rules at all.
        context.stroke(
            Path { $0.move(to: CGPoint(x: 0, y: y))
                $0.addLine(to: CGPoint(x: size.width, y: y))
            },
            with: .color(.skyAccent),
            style: StrokeStyle(lineWidth: 1.5, lineCap: .round, dash: [6, 4])
        )
        let label = context.resolve(
            Text("\(layer.cover) \(layer.baseFeet) ft").font(.caption2.weight(.medium)).foregroundStyle(Color.skyAccent)
        )
        let measured = label.measure(in: size)
        // Above the puffs, not just above the line: the band is centred on the line and reaches a puff
        // radius either side, so a label one label-height up lands inside the cloud.
        context.draw(
            label,
            at: CGPoint(x: 0, y: max(y - puffRadius - measured.height / 2 - Self.labelGap, measured.height / 2)),
            anchor: .leading
        )
    }

    /// The height the top of the diagram represents.
    ///
    /// Rounded up to a round number above the highest layer so the axis labels are readable, and floored so a
    /// clear sky is not an empty box with no sense of scale.
    private static func scaleTopFeet(for layers: [SkyLayer]) -> Int {
        let highest = layers.map(\.baseFeet).max() ?? 0
        let padded = Int((Double(highest) * scaleHeadroom).rounded())
        let rounded = ((padded + axisStepFeet - 1) / axisStepFeet) * axisStepFeet
        return max(rounded, minimumTopFeet)
    }

    private static func y(forFeet feet: Int, topFeet: Int, height: CGFloat) -> CGFloat {
        height * min(max(1 - CGFloat(feet) / CGFloat(topFeet), 0), 1)
    }

    private static let height: CGFloat = 200
    private static let minimumTopFeet = 5_000
    private static let axisStepFeet = 2_000
    private static let scaleHeadroom = 1.25
    private static let cloudAreaFraction = 0.72
    private static let maximumPuffs = 7
    private static let maximumPuffRadius: Double = 22
    private static let cloudOpacity: Double = 0.55
    private static let axisOpacity: Double = 0.15
    private static let labelGap: CGFloat = 4
}

#Preview {
    SkyLayersDiagram(
        layers: [
            SkyLayer(cover: "FEW", baseFeet: 1_200, coverFraction: 0.25, isCeiling: false),
            SkyLayer(cover: "SCT", baseFeet: 2_500, coverFraction: 0.5, isCeiling: false),
            SkyLayer(cover: "BKN", baseFeet: 4_800, coverFraction: 0.75, isCeiling: true),
        ],
        announcement: "Ceiling broken at 4800 feet"
    )
    .frostedCard()
    .padding()
}
