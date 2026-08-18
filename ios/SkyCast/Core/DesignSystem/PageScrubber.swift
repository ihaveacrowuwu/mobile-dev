import SwiftUI
import UIKit

/// The page indicator for a paged view: dots you can also drag.
///
/// Wraps `UIPageControl`, whose `allowsContinuousInteraction` scrubs pages under the finger and
/// plays the system haptic on each change. It also supplies Dynamic Type sizing, high-contrast
/// treatment, RTL ordering, and a VoiceOver adjustable trait that lets a screen-reader user swipe
/// up and down to change page.
///
/// SwiftUI has no native equivalent: `TabView`'s own `indexDisplayMode` dots are not draggable and
/// cannot be positioned, since they are pinned to the bottom of the pager, which on Home is behind
/// the tab bar. Hence the `UIViewRepresentable`.
///
/// The Android counterpart is `core/designsystem/component/PageScrubber.kt`, which draws the dots
/// and drives the haptics by hand because Compose has no equivalent either.
struct PageScrubber: UIViewRepresentable {
    let count: Int
    @Binding var selection: Int
    /// Spoken instead of "page 2 of 5" alone, so the announcement names the place.
    let announcement: String

    func makeUIView(context: Context) -> UIPageControl {
        let control = UIPageControl()
        control.addTarget(
            context.coordinator,
            action: #selector(Coordinator.pageChanged(_:)),
            for: .valueChanged
        )
        // The default is already true on iOS 14+; set explicitly because it is the entire reason
        // this type exists, and a future default flip would silently remove the behaviour.
        control.allowsContinuousInteraction = true
        control.backgroundStyle = .minimal
        control.currentPageIndicatorTintColor = UIColor(Color.skyAccent)
        control.pageIndicatorTintColor = UIColor(Color.secondaryLabel.opacity(indicatorOpacity))
        return control
    }

    func updateUIView(_ control: UIPageControl, context: Context) {
        control.numberOfPages = count
        // Only when it differs, or assigning during a drag fights the gesture.
        if control.currentPage != selection {
            control.currentPage = selection
        }
        control.accessibilityLabel = announcement
        context.coordinator.onChange = { selection = $0 }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    final class Coordinator: NSObject {
        var onChange: ((Int) -> Void)?

        @objc
        func pageChanged(_ sender: UIPageControl) {
            onChange?(sender.currentPage)
        }
    }

    private let indicatorOpacity: Double = 0.35
}

private extension Color {
    static let secondaryLabel = Color(uiColor: .secondaryLabel)
}

#Preview {
    @Previewable @State var selection = 1
    return PageScrubber(count: 4, selection: $selection, announcement: "Showing Malé, 2 of 4")
        .padding()
}
