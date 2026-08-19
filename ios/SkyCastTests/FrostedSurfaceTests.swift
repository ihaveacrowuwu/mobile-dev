import Testing
@testable import SkyCast

/// Guards the card surface's accessibility gate.
///
/// A material turns opaque by itself under **Reduce Transparency**, and `frostedCard` scales that
/// material down, so the gate that stops it scaling down in that state is what keeps the surface
/// accessible.
///
/// Guarded here rather than from a screenshot: the simulator ignores a written
/// `com.apple.Accessibility ReduceTransparencyEnabled` default even with
/// `com.apple.Accessibility.ReduceTransparencyChanged` posted, and the app renders identically to
/// the pixel with it on and off.
@Suite("Frosted card surface")
struct FrostedSurfaceTests {
    @Test("Reduce Transparency keeps the material fully opaque")
    func reduceTransparencyIsOpaque() {
        #expect(FrostedCard.materialOpacity(reduceTransparency: true) == 1)
    }

    @Test("Without it, the material is thinned so the background reads through")
    func defaultIsThinned() {
        let opacity = FrostedCard.materialOpacity(reduceTransparency: false)
        #expect(opacity == FrostedCard.thinness)
        #expect(opacity < 1)
        // A floor as well as a ceiling: at zero there is no frost at all, only a rim, and the cards would
        // stop being surfaces. The measured value is 0.4.
        #expect(opacity > 0.15)
    }
}
