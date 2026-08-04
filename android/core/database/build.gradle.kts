plugins {
    alias(libs.plugins.skycast.android.library)
    alias(libs.plugins.skycast.android.room)
    alias(libs.plugins.skycast.hilt)
}

android { namespace = "com.nauhaan.skycast.core.database" }

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(libs.kotlinx.coroutines.android)
}
