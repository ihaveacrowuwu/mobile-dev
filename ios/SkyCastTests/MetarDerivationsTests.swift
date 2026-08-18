import Foundation
import Testing
@testable import SkyCast

/// The values the METAR screen derives rather than reads.
///
/// A METAR reports temperature, dew point, altimeter and cloud layers; it never reports humidity, fog risk,
/// density altitude or the ceiling. Those are the four figures a pilot works out, and computing them is what
/// makes the screen worth more than a decoded table.
///
/// ## Where the expected numbers come from
///
/// Not from this code. Density altitude is checked against the worked example every ground-school text uses,
/// a 5000 ft field on a standard-pressure day at 25 °C gives 7400 ft, and against the definition itself,
/// which is that a standard day at sea level is zero. Humidity is checked against published psychrometric
/// tables: 26/14 is about 48%, 20/5 about 38%.
///
/// The Kotlin twin is `MetarDerivationsTest.kt`, asserting the same values.
@Suite("METAR derivations")
struct MetarDerivationsTests {
    // MARK: - Ceiling

    @Test("Only broken and overcast layers count as a ceiling")
    func ceilingIgnoresScatteredCloud() {
        // The assertion that matters, and the one an implementation gets wrong by taking the lowest cloud:
        // you can climb through gaps in scattered cloud under visual rules, which is why the flight-category
        // thresholds are defined against BKN and OVC alone. FEW at 1200 ft is not a 1200 ft ceiling.
        let report = Self.report(clouds: [
            CloudLayer(cover: "FEW", baseFeet: 1_200),
            CloudLayer(cover: "SCT", baseFeet: 2_500),
            CloudLayer(cover: "BKN", baseFeet: 4_800),
            CloudLayer(cover: "OVC", baseFeet: 7_000),
        ])
        #expect(report.ceilingFeet == 4_800)
    }

    @Test("A sky with no broken or overcast layer has no ceiling")
    func noCeiling() {
        #expect(Self.report(clouds: [CloudLayer(cover: "SCT", baseFeet: 4_800)]).ceilingFeet == nil)
        // NCD, nothing detected, decodes to no layers, which is also no ceiling rather than zero.
        #expect(Self.report(clouds: []).ceilingFeet == nil)
    }

    // MARK: - Humidity and the spread

    @Test("Relative humidity matches the psychrometric tables")
    func humidityMatchesTables() {
        #expect(Self.report(temperature: 26, dewPoint: 14).relativeHumidityPercent == 48)
        #expect(Self.report(temperature: 20, dewPoint: 5).relativeHumidityPercent == 37)
    }

    @Test("Saturated air is a hundred percent")
    func saturatedAir() {
        // The definition: dew point equal to temperature *is* saturation. A formula returning 99 or 101 here
        // would be visibly wrong on a foggy morning, which is exactly when someone reads this screen.
        #expect(Self.report(temperature: 14, dewPoint: 14).relativeHumidityPercent == 100)
        #expect(Self.report(temperature: -3, dewPoint: -3).relativeHumidityPercent == 100)
    }

    @Test("The dew point spread is the gap between the two")
    func spread() {
        #expect(Self.report(temperature: 26, dewPoint: 14).dewPointSpreadCelsius == 12)
        #expect(Self.report(temperature: 14, dewPoint: 14).dewPointSpreadCelsius == 0)
    }

    @Test("Derived values are absent when their inputs are")
    func absentInputs() {
        // A METAR can omit temperature and dew point entirely; the screen must show nothing rather than a
        // confident zero.
        let bare = Self.report(temperature: nil, dewPoint: nil, altimeter: nil)
        #expect(bare.relativeHumidityPercent == nil)
        #expect(bare.dewPointSpreadCelsius == nil)
        #expect(bare.densityAltitudeFeet == nil)
    }

    // MARK: - Density altitude

    @Test("Density altitude matches the ground-school worked example")
    func densityAltitudeWorkedExample() throws {
        // 5000 ft field, standard pressure, 25 °C. ISA at 5000 ft is 5 °C, so the air is 20 °C warm and the
        // field performs like 5000 + 20 × 120 = 7400 ft.
        let report = Self.report(
            elevationMetres: Int(5_000 / 3.28084),
            temperature: 25,
            altimeter: 1_013.25
        )
        let density = try #require(report.densityAltitudeFeet)
        #expect(abs(density - 7_400) <= 15)
    }

    @Test("A standard day at sea level is zero")
    func standardDay() throws {
        // The definition of the standard atmosphere, and a check no worked example can fake.
        let report = Self.report(elevationMetres: 0, temperature: 15, altimeter: 1_013.25)
        let density = try #require(report.densityAltitudeFeet)
        #expect(abs(density) <= 1)
    }

    @Test("Hotter and lower-pressure air raises the density altitude")
    func directions() throws {
        let cool = try #require(Self.report(elevationMetres: 10, temperature: 5, altimeter: 1_013.25)
            .densityAltitudeFeet)
        let warm = try #require(Self.report(elevationMetres: 10, temperature: 30, altimeter: 1_013.25)
            .densityAltitudeFeet)
        let low = try #require(Self.report(elevationMetres: 10, temperature: 5, altimeter: 990).densityAltitudeFeet)

        // The two directions that make the figure worth showing at all.
        #expect(warm > cool)
        #expect(low > cool)
    }

    private static func report(
        elevationMetres: Int = 10,
        temperature: Double? = 26,
        dewPoint: Double? = 14,
        altimeter: Double? = 1_009,
        clouds: [CloudLayer] = []
    )
        -> MetarReport
    {
        MetarReport(
            stationID: "EGLC",
            stationName: "London City",
            distanceKm: 12.7,
            latitude: 51.5,
            longitude: 0.05,
            elevationMetres: elevationMetres,
            observedAt: Date(timeIntervalSince1970: 0),
            temperatureCelsius: temperature,
            dewPointCelsius: dewPoint,
            windDirectionDegrees: 250,
            windSpeedKnots: 11,
            visibilityStatuteMiles: 6,
            visibilityIsOrGreater: true,
            altimeterHectopascals: altimeter,
            clouds: clouds,
            flightCategory: .vfr,
            raw: "METAR EGLC 181450Z AUTO 25011KT 9999 NCD 26/14 Q1009",
            cachedAt: Date(timeIntervalSince1970: 0)
        )
    }
}
