plugins {
    alias(libs.plugins.skycast.jvm.library)
}

// Pure Kotlin, and the second module where that is load-bearing: repository *interfaces* and
// use cases must not know Retrofit or Room exist. `import androidx.room.Dao` here is a compile
// error, which is what makes every view model testable with a hand-written fake.
dependencies {
    api(projects.core.model)
    api(projects.core.common)
    api(libs.kotlinx.coroutines.core)
    // javax.inject only, the annotations, not a DI framework. Hilt lives in the impl modules.
    api(libs.javax.inject)
}
