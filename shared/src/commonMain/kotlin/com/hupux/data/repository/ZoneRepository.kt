package com.hupux.data.repository

import com.hupux.data.model.ZoneCategory
import com.hupux.data.model.ZonePage
import com.hupux.data.ioDispatcher
import com.hupux.data.scraper.HupuScraper
import kotlinx.coroutines.withContext

class ZoneRepository(private val scraper: HupuScraper) {
    suspend fun getZoneList(): List<ZoneCategory> = withContext(ioDispatcher) {
        scraper.fetchZoneList()
    }

    suspend fun getZonePosts(topicId: Int, cursor: String? = null): ZonePage =
        withContext(ioDispatcher) {
            scraper.fetchZone(topicId, cursor)
        }
}
