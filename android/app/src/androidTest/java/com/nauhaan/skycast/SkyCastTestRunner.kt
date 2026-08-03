package com.nauhaan.skycast

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Instrumentation runner that swaps [SkyCastApplication] for Hilt's generated
 * [HiltTestApplication].
 *
 * Required by every `@HiltAndroidTest`: without it the real `@HiltAndroidApp` component
 * is installed and test modules cannot replace bindings. Registered as
 * `testInstrumentationRunner` in `app/build.gradle.kts`.
 */
class SkyCastTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
