package com.nauhaan.skycast.core.common

import kotlinx.coroutines.flow.Flow

/**
 * Observes whether the device currently has validated internet access.
 *
 * The **interface** lives in pure-Kotlin `:core:common` while its ConnectivityManager-backed
 * implementation lives in `:core:network`. That split is deliberate: `:core:data` needs to ask
 * "are we online?" without gaining a dependency on Android, and a test needs to answer that
 * question synthetically. "What does the Today screen look like offline?" is therefore a unit
 * test rather than a manual aeroplane-mode check.
 */
interface NetworkMonitor {
    /** Emits the current state immediately, then on every change. */
    val isOnline: Flow<Boolean>
}
