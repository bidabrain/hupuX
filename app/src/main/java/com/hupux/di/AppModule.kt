package com.hupux.di

import android.content.Context
import androidx.room.Room
import com.hupux.data.local.FavoriteDao
import com.hupux.data.local.FavoriteDatabase
import com.hupux.data.local.FollowedZoneDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): FavoriteDatabase =
        Room.databaseBuilder(ctx, FavoriteDatabase::class.java, "favorites.db")
            .fallbackToDestructiveMigration()   // dev only — drops old data on schema change
            .build()

    @Provides
    @Singleton
    fun provideFavoriteDao(db: FavoriteDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    @Singleton
    fun provideFollowedZoneDao(db: FavoriteDatabase): FollowedZoneDao = db.followedZoneDao()
}
