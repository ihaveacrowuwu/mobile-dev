import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.kotlin.dsl.configure

/**
 * Room for the module that owns the database.
 *
 * The exported schema location moves with the module, schemas now live in
 * `core/database/schemas/` rather than `app/schemas/`. They stay committed so a schema change
 * without a migration is caught in review rather than by a crash on a user's device.
 */
class SkycastAndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

            extensions.configure<KspExtension> {
                arg("room.schemaLocation", "${projectDir}/schemas")
                arg("room.generateKotlin", "true")
            }

            dependencies {
                add("implementation", libs.findLibrary("androidx-room-runtime").get())
                add("implementation", libs.findLibrary("androidx-room-ktx").get())
                add("ksp", libs.findLibrary("androidx-room-compiler").get())
            }
        }
    }
}
