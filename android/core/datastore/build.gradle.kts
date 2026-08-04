plugins {
    alias(libs.plugins.skycast.android.library)
    alias(libs.plugins.skycast.hilt)
}

android { namespace = "com.nauhaan.skycast.core.datastore" }

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
}
