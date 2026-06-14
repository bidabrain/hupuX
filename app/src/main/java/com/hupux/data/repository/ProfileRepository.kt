package com.hupux.data.repository

import com.hupux.data.local.CookiePreferences
import com.hupux.data.model.UserProfile
import com.hupux.data.model.Zone
import com.hupux.data.scraper.HupuDesktopScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val scraper: HupuDesktopScraper,
    private val cookiePrefs: CookiePreferences
) {
    fun isLoggedIn(): Boolean = cookiePrefs.isLoggedIn

    suspend fun fetchProfile(): UserProfile = withContext(Dispatchers.IO) {
        val uid = cookiePrefs.extractUid() ?: error("未登录")
        scraper.fetchUserProfile(uid)
    }

    suspend fun fetchFollowedZones(): List<Zone> = withContext(Dispatchers.IO) {
        val uid = cookiePrefs.extractUid() ?: error("未登录")
        scraper.fetchFollowedZones(uid)
    }
}
