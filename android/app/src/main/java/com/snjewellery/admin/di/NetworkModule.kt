package com.snjewellery.admin.di

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.snjewellery.admin.data.remote.TransportFailureInterceptor
import com.snjewellery.admin.domain.config.ConfigRepository
import com.snjewellery.admin.domain.config.ConfigStatus
import com.snjewellery.admin.ui.theme.Tokens
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * The HTTP stack: one OkHttp client, shared by Supabase and by Coil.
 *
 * Sharing matters here rather than being tidy for its own sake. This app
 * runs on a phone on Indian mobile data, and two clients would mean two
 * connection pools, two TLS handshakes to the same Supabase host, and
 * two places a timeout could be set differently.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Timeouts are generous on purpose. M7 uploads photographs over
     * mobile data against a thirty-second budget for the whole flow, and
     * a default ten-second read timeout would fail a slow but working
     * connection — which reads to the owner as the app being broken.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        transportFailureInterceptor: TransportFailureInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        // Sits below the Supabase SDK, which flattens every transport
        // exception into HttpRequestException and discards the cause. This
        // is the only place the real reason is still available.
        .addInterceptor(transportFailureInterceptor)
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        // A dropped connection mid-upload is the common case on mobile
        // data, and retrying the connection is cheaper than making the
        // owner photograph the piece again.
        .retryOnConnectionFailure(true)
        .build()

    /**
     * The Supabase client, built from the configuration the repository
     * reports.
     *
     * **Created on first injection, not at startup.** Hilt only calls
     * this when something asks for a client, so a build with no
     * credentials reaches the shell screen and reports what is missing
     * rather than crashing before it draws — which is what makes the
     * error message in M6.4 useful instead of academic.
     *
     * The key is always the anon key. Write permission comes from the
     * authenticated admin's session (M6.7) and from RLS, never from the
     * key — ADR-0004.
     */
    @Provides
    @Singleton
    fun provideSupabaseClient(
        configRepository: ConfigRepository,
        okHttpClient: OkHttpClient,
    ): SupabaseClient {
        val config = when (val status = configRepository.status()) {
            is ConfigStatus.Ready -> status.config
            is ConfigStatus.Incomplete -> error(
                "Supabase is not configured: ${status.missing.joinToString(", ")} " +
                    "missing from local.properties. See docs/architecture/android-build.md §3.",
            )
        }

        return createSupabaseClient(
            supabaseUrl = config.url,
            supabaseKey = config.anonKey,
        ) {
            install(Auth)
            install(Postgrest)
            install(Storage)

            // `preconfigured` is what makes the sharing real — without
            // it, Ktor builds its own OkHttp client and the pool is not
            // shared with Coil after all.
            httpEngine = OkHttp.create { preconfigured = okHttpClient }
        }
    }

    /**
     * Coil's image loader.
     *
     * A disk cache is the point of it: the owner scrolls the same product
     * list repeatedly through a working day, and re-downloading the same
     * photographs on mobile data is the most obvious waste this app could
     * commit. Coil does not enable one by default.
     *
     * Crossfade is at `base` from the motion tokens rather than Coil's
     * own default — motion.md rule 1 forbids a duration written by hand.
     */
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: PlatformContext,
        okHttpClient: OkHttpClient,
    ): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
        }
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(context, MEMORY_CACHE_FRACTION)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve(IMAGE_CACHE_DIR).toOkioPath())
                .maxSizeBytes(DISK_CACHE_BYTES)
                .build()
        }
        .crossfade(Tokens.Motion.BASE_MS.toInt())
        .build()

    private const val CONNECT_TIMEOUT_SECONDS = 30L
    private const val READ_TIMEOUT_SECONDS = 60L
    private const val WRITE_TIMEOUT_SECONDS = 120L

    /** Fraction of available heap for decoded bitmaps. */
    private const val MEMORY_CACHE_FRACTION = 0.20

    private const val IMAGE_CACHE_DIR = "image_cache"
    private const val DISK_CACHE_BYTES = 256L * 1024 * 1024
}
