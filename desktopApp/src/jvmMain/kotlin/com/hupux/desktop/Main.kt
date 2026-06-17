package com.hupux.desktop

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
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
import com.hupux.desktop.di.desktopModule
import org.koin.core.context.startKoin

fun main() {
    val koin = startKoin { modules(desktopModule) }.koin

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "HupuX",
            icon = painterResource("icon.png"),
            state = WindowState(size = DpSize(1100.dp, 750.dp))
        ) {
            App(
                homeRepo       = koin.get<HomeRepository>(),
                zoneRepo       = koin.get<ZoneRepository>(),
                favRepo        = koin.get<FavoritesRepository>(),
                followedRepo   = koin.get<FollowedZonesRepository>(),
                profileRepo    = koin.get<ProfileRepository>(),
                messageRepo    = koin.get<MessageRepository>(),
                scraper        = koin.get<HupuScraper>(),
                desktopScraper = koin.get<HupuDesktopScraper>(),
                imageUploader  = koin.get<DesktopImageUploader>(),
                cookieStorage  = koin.get<DesktopCookieStorage>()
            )
        }
    }
}
