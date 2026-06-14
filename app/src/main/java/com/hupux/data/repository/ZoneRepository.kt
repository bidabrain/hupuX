package com.hupux.data.repository

import com.hupux.data.model.ZoneCategory
import com.hupux.data.model.ZonePage
import com.hupux.data.scraper.HupuScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZoneRepository @Inject constructor(private val scraper: HupuScraper) {
    suspend fun getZoneList(): List<ZoneCategory> = withContext(Dispatchers.IO) {
        scraper.fetchZoneList()
    }

    suspend fun getZonePosts(topicId: Int, cursor: String? = null): ZonePage =
        withContext(Dispatchers.IO) {
            scraper.fetchZone(topicId, cursor)
        }
}
