plugins {
    alias(libs.plugins.skycast.android.library)
    alias(libs.plugins.skycast.hilt)
}

android { namespace = "com.nauhaan.skycast.core.data" }

// The only module that depends on BOTH the domain interfaces and every concrete data source.
// That is precisely its job: it implements the former using the latter, and nothing above it
// needs to know which sources exist.
dependencies {
    api(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.network)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(libs.kotlinx.coroutines.android)
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // ApiContractTest decodes the captured payloads with the same Json config NetworkModule
    // uses, so it needs the serialization runtime directly rather than transitively.
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(projects.core.testing)
}
