package com.hupux.data.repository

import com.hupux.data.CookieStorage
import com.hupux.data.model.UserProfile
import com.hupux.data.model.UserThreadPage
import com.hupux.data.model.Zone
import com.hupux.data.scraper.HupuDesktopScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProfileRepository(
    private val scraper: HupuDesktopScraper,
    private val cookieStorage: CookieStorage
) {
    fun isLoggedIn(): Boolean = cookieStorage.isLoggedIn

    suspend fun fetchProfile(): UserProfile = withContext(Dispatchers.IO) {
        val uid = cookieStorage.extractUid() ?: error("未登录")
        scraper.fetchUserProfile(uid)
    }

    suspend fun fetchFollowedZones(): List<Zone> = withContext(Dispatchers.IO) {
        val uid = cookieStorage.extractUid() ?: error("未登录")
        scraper.fetchFollowedZones(uid)
    }

    suspend fun fetchFavoriteList(maxTime: Long = 0): UserThreadPage = withContext(Dispatchers.IO) {
        val uid = cookieStorage.extractUid() ?: error("未登录")
        scraper.fetchFavoriteList(uid, maxTime)
    }

    suspend fun fetchUnreadMessageCount(): Int = withContext(Dispatchers.IO) {
        scraper.fetchUnreadMessageCount()
    }
}
