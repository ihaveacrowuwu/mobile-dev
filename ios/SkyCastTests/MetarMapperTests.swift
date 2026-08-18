import Foundation
import Testing
@testable import SkyCast

/// The METAR mapper, against a response captured from the live API.
///
/// The fixture is real, not hand-written, and is shared byte-for-byte with
/// `android/core/data/src/test/resources/fixtures/`. This API types two of its fields loosely:
/// `visib` arrives as the *string* `"6+"` and `wdir` can be `"VRB"`.
///
/// The expected values are identical to `MetarMapperTest.kt`, so the two platforms cannot disagree
/// about which airport is nearest or how far away it is.
@Suite("METAR mapping")
struct MetarMapperTests {
    private let now = Date(timeIntervalSince1970: 1_787_065_200)

    private func stations() throws -> [MetarDTO] {
        let url = try #require(
            Bundle(for: FixtureAnchor.self).url(forResource: "metar_london", withExtension: "json")
        )
        return try JSONDecoder().decode([MetarDTO].self, from: Data(contentsOf: url))
    }

    @Test("The real response decodes without loss")
    func realResponseDecodes() throws {
        let report = try #require(
            MetarMapper.nearestReport(from: stations(), latitude: 51.5074, longitude: -0.1278, cachedAt: now)
        )

        #expect(report.raw.contains(report.stationID))
        #expect(report.flightCategory == .vfr)
        #expect(!report.stationName.isEmpty)
        #expect(report.temperatureCelsius == 26)
        #expect(report.altimeterHectopascals == 1_010)
    }

    @Test("The nearest station wins, not the first in the response")
    func nearestNotFirst() throws {
        let all = try stations()
        let report = MetarMapper.nearestReport(from: all, latitude: 51.5074, longitude: -0.1278, cachedAt: now)

        // Taking `first` would have looked correct in testing and quietly shown the wrong airport:
        // this capture lists Biggin Hill before London City, which is nearer.
        #expect(all.first?.icaoID == "EGKB")
        #expect(report?.stationID == "EGLC")
        #expect((report?.distanceKm ?? 0) > 0)
    }

    @Test("A plus on the visibility is kept as a floor rather than a measurement")
    func visibilityFloor() throws {
        let report = try MetarMapper.nearestReport(
            from: stations(),
            latitude: 51.5074,
            longitude: -0.1278,
            cachedAt: now
        )

        #expect(report?.visibilityStatuteMiles == 6)
        #expect(report?.visibilityIsOrGreater == true)
    }

    @Test("A clear sky has no layers, and that is not a decoding failure")
    func clearSkyHasNoLayers() throws {
        // London City reported NCD, no cloud detected, so an empty list is the correct decode.
        // Asserting the layers were non-empty would fail on a cloudless day and pass on a parser
        // that invented one.
        let report = try MetarMapper.nearestReport(
            from: stations(),
            latitude: 51.5074,
            longitude: -0.1278,
            cachedAt: now
        )

        #expect(report?.stationID == "EGLC")
        #expect(report?.clouds.isEmpty == true)
    }

    @Test("Reported layers are decoded with their heights")
    func layersDecode() throws {
        let report = try MetarMapper.nearestReport(from: stations(), latitude: 51.15, longitude: -0.18, cachedAt: now)

        #expect(report?.stationID == "EGKK")
        #expect(report?.clouds.first?.cover == "FEW")
        #expect(report?.clouds.first?.baseFeet == 4_700)
    }

    @Test("A variable wind direction is absent rather than north")
    func variableWind() throws {
        let json = """
        [{"icaoId":"EGLL","name":"Heathrow","lat":51.4,"lon":-0.4,"wdir":"VRB",
          "rawOb":"METAR EGLL 181320Z VRB03KT","fltCat":"VFR"}]
        """
        let stations = try JSONDecoder().decode([MetarDTO].self, from: Data(json.utf8))

        let report = MetarMapper.nearestReport(from: stations, latitude: 51.5, longitude: -0.1, cachedAt: now)

        #expect(report?.windDirectionDegrees == nil)
    }

    @Test("Stations with no observation are skipped rather than shown half empty")
    func skipsIncomplete() throws {
        let json = """
        [{"icaoId":"EGLL","lat":51.4,"lon":-0.4},
         {"icaoId":"EGKK","name":"Gatwick","lat":51.15,"lon":-0.18,
          "rawOb":"METAR EGKK 181320Z 27010KT","fltCat":"VFR"}]
        """
        let stations = try JSONDecoder().decode([MetarDTO].self, from: Data(json.utf8))

        let report = MetarMapper.nearestReport(from: stations, latitude: 51.5, longitude: -0.1, cachedAt: now)

        // Heathrow is nearer but carries no report; an entry with no observation is not a reading.
        #expect(report?.stationID == "EGKK")
    }

    @Test("An empty response yields nothing rather than an empty report")
    func emptyResponse() {
        #expect(MetarMapper.nearestReport(from: [], latitude: 51.5, longitude: -0.1, cachedAt: now) == nil)
    }

    @Test("Distance is a great-circle figure, not a flat approximation")
    func greatCircleDistance() {
        // London to Malé, which any plane-geometry shortcut gets badly wrong.
        let km = MetarMapper.distanceKm(
            fromLatitude: 51.5074,
            fromLongitude: -0.1278,
            toLatitude: 4.1755,
            toLongitude: 73.5093
        )

        #expect(abs(km - 8_517) < 5)
    }
}

/// Anchors `Bundle(for:)` to the test bundle, which is where the fixtures are copied.
private final class FixtureAnchor {}
