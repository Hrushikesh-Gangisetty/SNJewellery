package com.snjewellery.admin.di

import com.snjewellery.admin.data.auth.SupabaseAdminAccessRepository
import com.snjewellery.admin.data.auth.SupabaseAuthRepository
import com.snjewellery.admin.data.catalogue.SupabaseCatalogueListRepository
import com.snjewellery.admin.data.catalogue.SupabaseCatalogueRepository
import com.snjewellery.admin.data.catalogue.SupabaseCategoryRepository
import com.snjewellery.admin.data.config.BuildConfigRepository
import com.snjewellery.admin.data.dashboard.SupabaseDashboardRepository
import com.snjewellery.admin.data.local.RoomDraftRepository
import com.snjewellery.admin.data.media.CacheStagedImages
import com.snjewellery.admin.data.remote.AndroidConnectivityMonitor
import com.snjewellery.admin.data.product.SupabaseProductImageRepository
import com.snjewellery.admin.data.product.SupabaseProductRepository
import com.snjewellery.admin.domain.auth.AdminAccessRepository
import com.snjewellery.admin.domain.auth.AuthRepository
import com.snjewellery.admin.domain.catalogue.CatalogueListRepository
import com.snjewellery.admin.domain.catalogue.CatalogueRepository
import com.snjewellery.admin.domain.catalogue.CategoryRepository
import com.snjewellery.admin.domain.config.ConfigRepository
import com.snjewellery.admin.domain.dashboard.DashboardRepository
import com.snjewellery.admin.domain.draft.DraftRepository
import com.snjewellery.admin.domain.media.StagedImages
import com.snjewellery.admin.domain.net.ConnectivityMonitor
import com.snjewellery.admin.domain.product.ProductImageRepository
import com.snjewellery.admin.domain.product.ProductRepository
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

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: SupabaseAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindAdminAccessRepository(
        impl: SupabaseAdminAccessRepository,
    ): AdminAccessRepository

    @Binds
    @Singleton
    abstract fun bindDashboardRepository(
        impl: SupabaseDashboardRepository,
    ): DashboardRepository

    @Binds
    @Singleton
    abstract fun bindCatalogueRepository(
        impl: SupabaseCatalogueRepository,
    ): CatalogueRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(
        impl: SupabaseCategoryRepository,
    ): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindCatalogueListRepository(
        impl: SupabaseCatalogueListRepository,
    ): CatalogueListRepository

    @Binds
    @Singleton
    abstract fun bindProductRepository(
        impl: SupabaseProductRepository,
    ): ProductRepository

    @Binds
    @Singleton
    abstract fun bindProductImageRepository(
        impl: SupabaseProductImageRepository,
    ): ProductImageRepository

    @Binds
    @Singleton
    abstract fun bindStagedImages(impl: CacheStagedImages): StagedImages

    @Binds
    @Singleton
    abstract fun bindDraftRepository(impl: RoomDraftRepository): DraftRepository

    @Binds
    @Singleton
    abstract fun bindConnectivityMonitor(
        impl: AndroidConnectivityMonitor,
    ): ConnectivityMonitor
}
