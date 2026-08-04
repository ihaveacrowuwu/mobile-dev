plugins {
    alias(libs.plugins.skycast.jvm.library)
}

// No dependencies.
//
// A pure Kotlin/JVM library, so the Android SDK, Room, Retrofit and Compose are all absent from its
// compile classpath. A domain model physically cannot import a framework type.

dependencies {
    testImplementation(libs.junit)
}
