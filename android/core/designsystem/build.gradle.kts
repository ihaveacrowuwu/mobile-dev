plugins {
    alias(libs.plugins.skycast.android.library)
    alias(libs.plugins.skycast.android.library.compose)
}

android { namespace = "com.nauhaan.skycast.core.designsystem" }

// Depends on :core:model so components can take a WeatherCondition directly, and on :core:common
// for AppError. NOT on :core:domain: the design system renders values, it does not call
// repositories.
dependencies {
    api(projects.core.model)
    api(projects.core.common)
    implementation(libs.androidx.compose.material.icons.extended)
}

dependencies {
    testImplementation(libs.junit)
}
