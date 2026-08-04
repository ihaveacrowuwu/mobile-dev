plugins {
    alias(libs.plugins.skycast.jvm.library)
}

// Pure Kotlin. AppError and DispatcherProvider must be usable from :core:domain, which cannot
// see Android, so neither can this. NetworkMonitor's *interface* lives here too (it is just
// Flow<Boolean>); its ConnectivityManager-backed implementation is in :core:network.
dependencies {
    api(projects.core.model)
    api(libs.kotlinx.coroutines.core)
}

dependencies {
    testImplementation(libs.junit)
}
