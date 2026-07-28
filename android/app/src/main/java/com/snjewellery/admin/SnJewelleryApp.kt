package com.snjewellery.admin

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point, and the root of the dependency graph.
 *
 * `@HiltAndroidApp` is what generates that graph; every `@Inject` in the
 * app resolves against it. See ADR-0007 for why Hilt rather than Koin —
 * in short, a missing binding fails the build instead of crashing on a
 * screen the owner opens once a week.
 */
@HiltAndroidApp
class SnJewelleryApp : Application()
