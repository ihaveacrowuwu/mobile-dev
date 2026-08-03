package com.nauhaan.skycast

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point.
 *
 * `@HiltAndroidApp` generates the dependency graph that every `@AndroidEntryPoint`
 * activity and `@HiltViewModel` resolves against.
 *
 * Empty otherwise: work done here runs on the main thread before the first frame, so anything
 * expensive belongs in an `Initializer` (androidx.startup) or a lazily-created singleton.
 */
@HiltAndroidApp
class SkyCastApplication : Application()
