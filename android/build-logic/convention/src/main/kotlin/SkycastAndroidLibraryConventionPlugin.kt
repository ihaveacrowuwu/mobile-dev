import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.JavaVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * An Android library module, for layers that genuinely need the Android SDK
 * (`:core:network`, `:core:database`, `:core:datastore`, `:core:data`, `:core:designsystem`).
 *
 * Centralising these settings means a compileSdk bump or a compiler flag changes in **one** place
 * for every module.
 *
 * Modules that must NOT see the Android SDK use [SkycastJvmLibraryConventionPlugin] instead.
 */
class SkycastAndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply("org.jetbrains.kotlin.android")
            }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

            extensions.configure<LibraryExtension> {
                compileSdk = libs.findVersion("compileSdk").get().toString().toInt()

                defaultConfig {
                    minSdk = libs.findVersion("minSdk").get().toString().toInt()
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                    // Lets minSdk 26 use java.time, matching :app.
                    isCoreLibraryDesugaringEnabled = true
                }

                // A library has no product to ship, so no BuildConfig by default. Modules that
                // need one opt in explicitly.
                buildFeatures {
                    buildConfig = false
                }
            }

            dependencies.add(
                "coreLibraryDesugaring",
                libs.findLibrary("desugar-jdk-libs").get(),
            )

            tasks.withType<KotlinCompile>().configureEach {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                    allWarningsAsErrors.set(
                        providers.gradleProperty("warningsAsErrors").orNull.toBoolean(),
                    )
                    freeCompilerArgs.addAll(
                        // Material 3 Expressive is adopted project-wide.
                        "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
                        "-Xconsistent-data-class-copy-visibility",
                        "-Xannotation-default-target=param-property",
                    )
                }
            }
        }
    }
}
