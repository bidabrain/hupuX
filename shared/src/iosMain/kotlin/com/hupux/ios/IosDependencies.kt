package com.hupux.ios

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.hupux.data.UpdateChecker
import com.hupux.data.repository.FollowedZonesRepository
import com.hupux.data.repository.HomeRepository
import com.hupux.data.repository.MessageRepository
import com.hupux.data.repository.ProfileRepository
import com.hupux.data.repository.ZoneRepository
import com.hupux.data.scraper.HupuDesktopScraper
import com.hupux.data.scraper.HupuScraper
import com.hupux.shared.db.HupuDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout

/**
 * iOS 端的依赖图，供 Swift 消费。Swift 侧只需 `let deps = IosDependencies()`，
 * 然后通过属性拿到各 repository / scraper / cookie。
 * 用手工装配而非 Koin —— Swift 直接读属性比在 Swift 里调 Koin 的 get() 友好得多。
 */
class IosDependencies {

    private val httpClient: HttpClient = HttpClient(Darwin) {
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 20_000
        }
    }

    private val driver = NativeSqliteDriver(HupuDatabase.Schema, "hupux.db")
    private val database = HupuDatabase(driver)

    /** 登录/发帖等需要读写 cookie，暴露给 Swift。 */
    val cookieStorage = IosCookieStorage()

    val hupuScraper = HupuScraper(httpClient)
    val desktopScraper = HupuDesktopScraper(httpClient, cookieStorage)
    val updateChecker = UpdateChecker(httpClient)

    val homeRepository = HomeRepository(hupuScraper)
    val zoneRepository = ZoneRepository(hupuScraper)
    val profileRepository = ProfileRepository(desktopScraper, cookieStorage)
    val messageRepository = MessageRepository(desktopScraper)
    val followedZonesRepository = FollowedZonesRepository(database)
}
