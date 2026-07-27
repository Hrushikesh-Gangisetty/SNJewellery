package com.snjewellery.admin

import android.app.Application

/**
 * Application entry point.
 *
 * Exists now, empty, because it is where the dependency graph is
 * installed in M6.4 — Hilt requires an `@HiltAndroidApp` Application, and
 * introducing one later means touching the manifest again. See
 * `docs/adr/0007-android-architecture.md`.
 */
class SnJewelleryApp : Application()
