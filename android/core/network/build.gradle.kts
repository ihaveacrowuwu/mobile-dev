plugins {
    alias(libs.plugins.skycast.android.library)
    alias(libs.plugins.skycast.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android { namespace = "com.nauhaan.skycast.core.network" }

dependencies {
    api(projects.core.common)
    implementation(projects.core.model)
    implementation(libs.bundles.networking)
    implementation(libs.kotlinx.coroutines.android)
}
