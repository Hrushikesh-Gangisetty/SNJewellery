package com.snjewellery.admin.di

import android.content.Context
import androidx.room.Room
import com.snjewellery.admin.data.local.AdminDatabase
import com.snjewellery.admin.data.local.MIGRATION_1_2
import com.snjewellery.admin.data.local.PendingDraftDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The device's database, which has to be constructed rather than
 * injected — so it gets its own module, and `DataModule` stays
 * bindings-only (android-app.md §3).
 *
 * **No `fallbackToDestructiveMigration`.** It is the usual line here and
 * it would silently drop the owner's unsent drafts on the first schema
 * change — the one thing this table exists to prevent. A version bump
 * without a migration should fail loudly in testing instead.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AdminDatabase =
        Room.databaseBuilder(context, AdminDatabase::class.java, DATABASE_NAME)
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun providePendingDraftDao(database: AdminDatabase): PendingDraftDao = database.pendingDrafts()

    private const val DATABASE_NAME = "sn-admin.db"
}
