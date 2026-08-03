plugins {
    // Lets us write Gradle plugins in Kotlin with the same DSL the build scripts use.
    `kotlin-dsl`
}

group = "com.nauhaan.skycast.buildlogic"

// Must match the JVM the main build runs on (see app/build.gradle.kts jvmToolchain(21)).
// A mismatch here produces "class file has wrong version" errors that point nowhere useful.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // compileOnly, not implementation: these plugins are already on the classpath of the
    // build that applies our conventions. Bundling them would risk two AGP versions loaded
    // at once.
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
}

// Each convention plugin gets an id that modules reference as
// `alias(libs.plugins.skycast.…)`. Registering them here is what makes those aliases
// resolvable.
gradlePlugin {
    plugins {
        register("jvmLibrary") {
            id = "skycast.jvm.library"
            implementationClass = "SkycastJvmLibraryConventionPlugin"
        }
        register("androidLibrary") {
            id = "skycast.android.library"
            implementationClass = "SkycastAndroidLibraryConventionPlugin"
        }
    }
}
