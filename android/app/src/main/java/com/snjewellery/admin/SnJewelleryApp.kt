package com.snjewellery.admin

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import javax.inject.Provider

/**
 * Application entry point, and the root of the dependency graph.
 *
 * `@HiltAndroidApp` is what generates that graph; every `@Inject` in the
 * app resolves against it. See ADR-0007 for why Hilt rather than Koin —
 * in short, a missing binding fails the build instead of crashing on a
 * screen the owner opens once a week.
 *
 * It also supplies Coil's singleton loader, so `AsyncImage` anywhere in
 * the app gets the configured one — shared OkHttp client, disk cache,
 * token-derived crossfade — rather than Coil's default with no disk
 * cache at all.
 */
@HiltAndroidApp
class SnJewelleryApp : Application(), SingletonImageLoader.Factory {

    /**
     * A [Provider], so the loader is built when the first image is
     * requested rather than during `onCreate`. Field injection has not
     * happened yet when Hilt constructs this class, and building an
     * image loader is not work worth doing on the launch path.
     */
    @Inject
    lateinit var imageLoader: Provider<ImageLoader>

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()
}
