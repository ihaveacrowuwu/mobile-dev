// Root build file. Plugins are declared with `apply false` here so that the
// versions resolve once from the version catalog, then each module opts in.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

// ── Static analysis applied uniformly to every module ──────────────────────
subprojects {
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)
    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set(rootProject.libs.versions.ktlintCli.get())
        android.set(true)
        ignoreFailures.set(false)
        // Rule configuration lives in the root .editorconfig so that the IDE,
        // the CLI and Gradle all agree.
        filter {
            exclude { it.file.path.contains("/build/") }
        }
        reporters {
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.SARIF)
        }
    }

    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        baseline = rootProject.file("config/detekt/baseline.xml").takeIf { it.exists() }
        parallel = true
        ignoreFailures = false
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = "17"
        reports {
            html.required.set(true)
            sarif.required.set(true)
            xml.required.set(false)
            txt.required.set(false)
        }
    }
    tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
        jvmTarget = "17"
    }
}

// Convenience: `./gradlew qualityCheck` runs every static check without needing
// a connected device. Used by scripts/lint.sh.
tasks.register("qualityCheck") {
    group = "verification"
    description = "Runs ktlint, detekt, Android Lint and JVM unit tests."
    // Aggregate across every module, not just :app. `test` picks up the JVM modules'
    // suites too, which `:app:testDebugUnitTest` alone would silently skip.
    dependsOn(
        subprojects.mapNotNull { it.tasks.findByName("ktlintCheck") },
        subprojects.mapNotNull { it.tasks.findByName("detekt") },
        ":app:lintDebug",
        ":app:testDebugUnitTest",
        ":core:model:test",
        ":core:common:test",
        ":core:designsystem:testDebugUnitTest",
        ":core:data:testDebugUnitTest",
    )
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
