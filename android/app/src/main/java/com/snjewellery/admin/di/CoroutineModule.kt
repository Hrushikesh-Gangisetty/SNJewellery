package com.snjewellery.admin.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * A scope that outlives every screen.
 *
 * The draft sync is not a screen's work: it carries on while the owner
 * moves between the dashboard and the form, and cancelling it because
 * they navigated would abandon an upload half-way.
 *
 * Injected rather than constructed inside the singleton that uses it, so
 * a test can supply its own and drive the sync to completion instead of
 * racing a real dispatcher.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {

    /**
     * `SupervisorJob`, so one draft failing in a way nobody caught does
     * not take the rest of the sync down with it.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
