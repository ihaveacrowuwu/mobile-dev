import Foundation
import Network

/// Observes whether the device currently has a usable network path.
///
/// A protocol so repository and view model tests can drive connectivity synthetically,
/// "what does the Home screen look like offline?" becomes a unit test rather than a
/// manual aeroplane-mode check.
protocol NetworkMonitoring: Sendable {
    /// The current state, sampled at the moment of the call.
    var isOnline: Bool { get async }
}

/// `NetworkMonitoring` backed by `NWPathMonitor`.
///
/// An `actor` because `NWPathMonitor` delivers updates on its own queue and the cached
/// value is mutable shared state. Under Swift 6 strict concurrency this is the difference
/// between correct code and a data race the compiler rejects.
actor NetworkMonitor: NetworkMonitoring {
    private let monitor = NWPathMonitor()
    private var isStarted = false

    /// Optimistic default: assume online until the monitor says otherwise, so the first
    /// request is attempted rather than pre-emptively blocked.
    private var currentlySatisfied = true

    var isOnline: Bool {
        get async {
            startIfNeeded()
            return currentlySatisfied
        }
    }

    private func startIfNeeded() {
        guard !isStarted else { return }
        isStarted = true

        monitor.pathUpdateHandler = { [weak self] path in
            let satisfied = path.status == .satisfied
            // Hop back onto the actor: the handler runs on the monitor's queue.
            Task { await self?.update(satisfied: satisfied) }
        }
        monitor.start(queue: DispatchQueue(label: "com.nauhaan.skycast.network-monitor"))
    }

    private func update(satisfied: Bool) {
        currentlySatisfied = satisfied
    }

    deinit {
        monitor.cancel()
    }
}

/// Test double. Lives in the app target rather than the test target so SwiftUI previews
/// can use it too.
struct StaticNetworkMonitor: NetworkMonitoring {
    let online: Bool

    init(online: Bool = true) {
        self.online = online
    }

    var isOnline: Bool {
        get async { online }
    }
}
