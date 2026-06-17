package com.hupux.di

import android.content.Context
import androidx.room.Room
import com.hupux.data.CookieStorage
import com.hupux.data.local.CookiePreferences
import com.hupux.data.local.FavoriteDao
import com.hupux.data.local.FavoriteDatabase
import com.hupux.data.local.FollowedZoneDao
import com.hupux.data.repository.HomeRepository
import com.hupux.data.repository.MessageRepository
import com.hupux.data.repository.ProfileRepository
import com.hupux.data.repository.ZoneRepository
import com.hupux.data.scraper.HupuDesktopScraper
import com.hupux.data.scraper.HupuScraper
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
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideFavoriteDao(db: FavoriteDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    @Singleton
    fun provideFollowedZoneDao(db: FavoriteDatabase): FollowedZoneDao = db.followedZoneDao()

    // CookiePreferences 实现了 CookieStorage，对外暴露为接口
    @Provides
    @Singleton
    fun provideCookieStorage(prefs: CookiePreferences): CookieStorage = prefs

    // 以下类已移至 shared 模块，不再有 @Inject，需手动 provide
    @Provides
    @Singleton
    fun provideHupuScraper(client: OkHttpClient): HupuScraper = HupuScraper(client)

    @Provides
    @Singleton
    fun provideHupuDesktopScraper(
        client: OkHttpClient,
        cookieStorage: CookieStorage
    ): HupuDesktopScraper = HupuDesktopScraper(client, cookieStorage)

    @Provides
    @Singleton
    fun provideHomeRepository(scraper: HupuScraper): HomeRepository = HomeRepository(scraper)

    @Provides
    @Singleton
    fun provideZoneRepository(scraper: HupuScraper): ZoneRepository = ZoneRepository(scraper)

    @Provides
    @Singleton
    fun provideProfileRepository(
        scraper: HupuDesktopScraper,
        cookieStorage: CookieStorage
    ): ProfileRepository = ProfileRepository(scraper, cookieStorage)

    @Provides
    @Singleton
    fun provideMessageRepository(scraper: HupuDesktopScraper): MessageRepository =
        MessageRepository(scraper)
}
