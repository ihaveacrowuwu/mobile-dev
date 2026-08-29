import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Reads the OpenWeather API key from `local.properties` (gitignored) and falls back
 * to the `OPEN_WEATHER_API_KEY` environment variable so a build server can inject it.
 *
 * Returns an empty string when neither is present. This is intentional: the build
 * must never fail because a secret is missing: the app detects the empty key at
 * runtime and shows a configuration screen instead.
 */
fun resolveApiKey(): String {
    val local = rootProject.file("local.properties")
    if (local.exists()) {
        val props = Properties().apply { local.inputStream().use { load(it) } }
        props.getProperty("OPEN_WEATHER_API_KEY")?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return System.getenv("OPEN_WEATHER_API_KEY") ?: ""
}

android {
    namespace = "com.nauhaan.skycast"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "com.nauhaan.skycast"
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.targetSdk
                .get()
                .toInt()
        versionCode = 1
        versionName = "1.0.0"

        // Custom runner so Hilt can install HiltTestApplication. See SkyCastTestRunner.
        testInstrumentationRunner = "com.nauhaan.skycast.SkyCastTestRunner"

        // Injected as BuildConfig constants rather than hardcoded in source.
        buildConfigField("String", "OPEN_WEATHER_API_KEY", "\"${resolveApiKey()}\"")
        buildConfigField("String", "OPEN_WEATHER_BASE_URL", "\"https://api.openweathermap.org/\"")
    }

    // Only ship the locales we actually provide, keeping the APK small.
    androidResources {
        localeFilters += listOf("en")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Debug signing so that `assembleRelease` is runnable on any machine
            // without a keystore. Replace with a real signing config before any
            // Play Store distribution.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Lets minSdk 26 use java.time and other newer APIs.
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes +=
                setOf(
                    "/META-INF/{AL2.0,LGPL2.1}",
                    "/META-INF/LICENSE*",
                    "/META-INF/DEPENDENCIES",
                    "META-INF/*.kotlin_module",
                )
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    lint {
        // Accessibility and correctness issues must not be merged.
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true
        sarifReport = true
        htmlReport = true
        disable += setOf(
            // Dependency currency is a deliberate, reviewed decision recorded in
            // gradle/libs.versions.toml, not something an upstream release should be
            // able to turn into a failing build.
            "GradleDependency",
            "NewerVersionAvailable",
            // AGP is pinned to 8.x. AGP 9 would force detekt, ktlint-gradle and Gradle itself
            // onto versions whose compatibility here is unverified.
            // See the `hilt` note in gradle/libs.versions.toml.
            "AndroidGradlePluginVersion",
            // Only English is shipped (androidResources.localeFilters), so there is
            // nothing to translate yet.
            "MissingTranslation",
        )
    }
}

// Kotlin compiler settings. Top-level, not inside `android { }`: this is the Kotlin
// Gradle Plugin's own extension, not an AGP one.
kotlin {
    // Pin the compiler JDK so the build is reproducible across machines. Bytecode still
    // targets 17 (see android.compileOptions) for maximum Android compatibility.
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // Enable with -PwarningsAsErrors=true.
        allWarningsAsErrors.set(providers.gradleProperty("warningsAsErrors").orNull.toBoolean())
        freeCompilerArgs.addAll(
            // Material 3 Expressive is adopted project-wide, so the opt-in lives here rather than
            // as an @OptIn on every file that touches an emphasized type role, MotionScheme or an
            // expressive component.
            //
            // ExperimentalMaterial3Api is NOT opted into globally. Those APIs are opted into per
            // use site, so the experimental signal stays visible.
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-Xconsistent-data-class-copy-visibility",
            // Opt in to Kotlin's future default: an annotation on a constructor
            // parameter also applies to the generated field. Without this, every
            // @StringRes / @Serializable constructor property emits a migration
            // warning, which -PwarningsAsErrors=true turns into an error.
            "-Xannotation-default-target=param-property",
        )
    }
}

dependencies {
    // ── Project modules ────────────────────────────────────────────────────
    // Type-safe accessor (see settings.gradle.kts): a renamed module is a compile error.
    // `api`, not `implementation`: domain models appear in this module's own public API
    // (view model state, composable parameters), so consumers need them transitively.
    api(projects.core.model)
    api(projects.core.common)
    api(projects.core.domain)
    api(projects.core.designsystem)
    implementation(projects.core.data)
    implementation(projects.core.network)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    testImplementation(projects.core.testing)

    // ── Platform / BOM ─────────────────────────────────────────────────────
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    // ── Core & lifecycle ───────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    // ── UI (Compose) ───────────────────────────────────────────────────────
    implementation(libs.androidx.activity.compose)
    implementation(libs.bundles.compose)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // ── Dependency injection ───────────────────────────────────────────────
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // ── Persistence ────────────────────────────────────────────────────────

    // ── Networking ─────────────────────────────────────────────────────────

    // ── Images ─────────────────────────────────────────────────────────────
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // ── Desugaring ─────────────────────────────────────────────────────────
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // ── Unit tests (JVM, no device needed) ─────────────────────────────────
    testImplementation(libs.bundles.unit.test)
    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.room.testing)

    // ── Instrumented tests (device / emulator) ─────────────────────────────
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
