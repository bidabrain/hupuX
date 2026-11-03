package com.hupux.data.repository

import com.hupux.data.model.ZoneCategory
import com.hupux.data.model.ZonePage
import com.hupux.data.model.ZoneSort
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

    /**
     * 按指定顺序取专区帖子。REPLY 与 [getZonePosts] 相同（含专区信息）；
     * POST 只返回帖子，zoneDetail 是占位，界面应沿用首次加载拿到的专区信息。
     * cursor 由同一排序的上一页返回，两种排序的 cursor 不能混用。
     */
    suspend fun getZonePosts(topicId: Int, cursor: String?, sort: ZoneSort): ZonePage =
        withContext(ioDispatcher) {
            when (sort) {
                ZoneSort.REPLY -> scraper.fetchZone(topicId, cursor)
                ZoneSort.POST  -> scraper.fetchZoneByPostTime(topicId, cursor?.toIntOrNull() ?: 1)
            }
        }
}
