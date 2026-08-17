plugins {
    alias(libs.plugins.skycast.android.library)
    alias(libs.plugins.skycast.android.room)
    alias(libs.plugins.skycast.hilt)
}

android {
    namespace = "com.nauhaan.skycast.core.database"
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets {
        // MigrationTestHelper reads the exported schema JSON from the APK's assets, so the
        // committed schemas/ directory is registered as an androidTest asset source. Without this
        // the helper cannot open a version-1 database and every migration test fails to start.
        getByName("androidTest") { assets.srcDir(files("$projectDir/schemas")) }
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(libs.kotlinx.coroutines.android)

    // Instrumented, not JVM: these tests exercise real SQLite and real Room codegen, which is
    // precisely where the bugs they guard against lived.
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.junit)
    // Explicit, unlike in :app where Espresso drags them in: without the runner the test APK has
    // no AndroidJUnitRunner class and the instrumentation crashes before discovering any test,
    // reported by Gradle as "0 tests", which reads like a source-set problem and is not one.
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
