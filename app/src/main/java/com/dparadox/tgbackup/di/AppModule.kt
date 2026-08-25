package com.dparadox.tgbackup.di

import android.content.Context
import com.dparadox.tgbackup.data.AppDatabase
import com.dparadox.tgbackup.gallery.db.dao.AlbumDao
import com.dparadox.tgbackup.gallery.db.dao.MediaItemDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing Room database access and the gallery DAOs.
 * MediaStoreDataSource and GalleryRepository are provided via their own
 * @Inject constructors (scoped @Singleton).
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getInstance(context)

    @Provides
    fun provideMediaItemDao(db: AppDatabase): MediaItemDao = db.mediaItemDao()

    @Provides
    fun provideAlbumDao(db: AppDatabase): AlbumDao = db.albumDao()
}
