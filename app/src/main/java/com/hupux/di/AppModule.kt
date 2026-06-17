package com.hupux.di

import androidx.lifecycle.SavedStateHandle
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.hupux.data.CookieStorage
import com.hupux.data.local.CookiePreferences
import com.hupux.data.repository.FavoritesRepository
import com.hupux.data.repository.FollowedZonesRepository
import com.hupux.shared.db.HupuDatabase
import com.hupux.data.repository.HomeRepository
import com.hupux.data.repository.MessageRepository
import com.hupux.data.repository.PostRepository
import com.hupux.data.repository.ProfileRepository
import com.hupux.data.repository.ZoneRepository
import com.hupux.data.scraper.HupuDesktopScraper
import com.hupux.data.scraper.HupuImageUploader
import com.hupux.data.scraper.HupuScraper
import com.hupux.ui.favorites.FavoritesViewModel
import com.hupux.ui.home.HomeViewModel
import com.hupux.ui.post.PostDetailViewModel
import com.hupux.ui.profile.LoginWebViewViewModel
import com.hupux.ui.profile.MessageViewModel
import com.hupux.ui.profile.NewPostViewModel
import com.hupux.ui.profile.ProfileViewModel
import com.hupux.ui.settings.SettingsViewModel
import com.hupux.ui.profile.UserFavoriteListViewModel
import com.hupux.ui.profile.UserRecommendListViewModel
import com.hupux.ui.profile.UserReplyListViewModel
import com.hupux.ui.profile.UserThreadListViewModel
import com.hupux.ui.profile.UserWebViewViewModel
import com.hupux.ui.zone.ZoneDetailViewModel
import com.hupux.ui.zone.ZoneListViewModel
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

val appModule = module {

    // ── Infrastructure ────────────────────────────────────────────────────────
    single<OkHttpClient> {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
    single<SqlDriver> {
        AndroidSqliteDriver(HupuDatabase.Schema, androidContext(), "favorites.db")
    }
    single<HupuDatabase> { HupuDatabase(get()) }

    // ── Cookie ────────────────────────────────────────────────────────────────
    single<CookiePreferences> { CookiePreferences(androidContext()) }
    single<CookieStorage>     { get<CookiePreferences>() }

    // ── Scrapers ──────────────────────────────────────────────────────────────
    single { HupuScraper(get()) }
    single { HupuDesktopScraper(get(), get<CookieStorage>()) }
    single { HupuImageUploader(get(), get<CookiePreferences>(), androidContext()) }

    // ── Repositories ──────────────────────────────────────────────────────────
    single { HomeRepository(get()) }
    single { ZoneRepository(get()) }
    single { ProfileRepository(get(), get<CookieStorage>()) }
    single { MessageRepository(get()) }
    single { FavoritesRepository(get<HupuDatabase>()) }
    single { FollowedZonesRepository(get<HupuDatabase>()) }
    single { PostRepository(get(), get(), get<CookiePreferences>(), get()) }

    // ── ViewModels ────────────────────────────────────────────────────────────
    viewModel { HomeViewModel(get(), get(), get()) }
    viewModel { FavoritesViewModel(get()) }
    viewModel { PostDetailViewModel(get(), get(), get<CookiePreferences>()) }
    viewModel { LoginWebViewViewModel(get<CookiePreferences>()) }
    viewModel { MessageViewModel(get()) }
    viewModel { NewPostViewModel(get(), androidContext()) }
    viewModel { ProfileViewModel(get(), get<CookiePreferences>()) }
    viewModel { UserFavoriteListViewModel(get()) }
    viewModel { (handle: SavedStateHandle) -> UserRecommendListViewModel(get(), handle) }
    viewModel { (handle: SavedStateHandle) -> UserReplyListViewModel(get(), handle) }
    viewModel { (handle: SavedStateHandle) -> UserThreadListViewModel(get(), handle) }
    viewModel { UserWebViewViewModel(get<CookiePreferences>()) }
    viewModel { SettingsViewModel(get<CookiePreferences>(), androidContext()) }
    viewModel { ZoneDetailViewModel(get(), get<CookiePreferences>()) }
    viewModel { ZoneListViewModel(get(), get()) }
}
