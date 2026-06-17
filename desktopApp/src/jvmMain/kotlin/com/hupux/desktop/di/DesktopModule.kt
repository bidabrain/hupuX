package com.hupux.desktop.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hupux.data.CookieStorage
import com.hupux.data.repository.FavoritesRepository
import com.hupux.data.repository.FollowedZonesRepository
import com.hupux.data.repository.HomeRepository
import com.hupux.data.repository.MessageRepository
import com.hupux.data.repository.ProfileRepository
import com.hupux.data.repository.ZoneRepository
import com.hupux.data.scraper.HupuDesktopScraper
import com.hupux.data.scraper.HupuScraper
import com.hupux.desktop.data.DesktopCookieStorage
import com.hupux.desktop.data.DesktopImageUploader
import com.hupux.shared.db.HupuDatabase
import okhttp3.OkHttpClient
import org.koin.dsl.module
import java.io.File
import java.util.concurrent.TimeUnit

val desktopModule = module {

    single<OkHttpClient> {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    single<DesktopCookieStorage> { DesktopCookieStorage() }
    single<CookieStorage> { get<DesktopCookieStorage>() }

    single<SqlDriver> {
        val dbDir = File(System.getProperty("user.home"), ".hupux")
        dbDir.mkdirs()
        val url = "jdbc:sqlite:${File(dbDir, "hupux.db").absolutePath}"
        JdbcSqliteDriver(url).also { HupuDatabase.Schema.create(it) }
    }
    single<HupuDatabase> { HupuDatabase(get()) }

    single { DesktopImageUploader(get(), get<DesktopCookieStorage>()) }
    single { HupuScraper(get()) }
    single { HupuDesktopScraper(get(), get<CookieStorage>()) }

    single { HomeRepository(get()) }
    single { ZoneRepository(get()) }
    single { ProfileRepository(get(), get<CookieStorage>()) }
    single { MessageRepository(get()) }
    single { FavoritesRepository(get<HupuDatabase>()) }
    single { FollowedZonesRepository(get<HupuDatabase>()) }
}
