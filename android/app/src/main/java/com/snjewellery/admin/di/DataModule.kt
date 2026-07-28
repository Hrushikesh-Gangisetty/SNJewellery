package com.snjewellery.admin.di

import com.snjewellery.admin.data.config.BuildConfigRepository
import com.snjewellery.admin.domain.config.ConfigRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds repository interfaces to their implementations.
 *
 * `@Binds` rather than `@Provides` wherever the implementation can be
 * constructed by injection: it generates no factory body and costs
 * nothing at runtime.
 *
 * This is the seam ADR-0007 chose Hilt for. Every binding is checked
 * when the app compiles, so a repository nobody wired up fails the build
 * rather than crashing on a screen the owner opens once a week.
 *
 * Repositories from M6.5 onward — catalogue, auth, upload — bind here
 * too. Sources that need construction (the Supabase client, Room) get
 * their own `@Provides` module rather than being crammed into this one.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindConfigRepository(impl: BuildConfigRepository): ConfigRepository
}
