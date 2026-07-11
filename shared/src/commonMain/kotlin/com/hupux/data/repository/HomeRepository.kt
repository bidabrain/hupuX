package com.hupux.data.repository

import com.hupux.data.model.HotItem
import com.hupux.data.model.Post
import com.hupux.data.model.TopicThreadPage
import com.hupux.data.ioDispatcher
import com.hupux.data.scraper.HupuScraper
import kotlinx.coroutines.withContext

class HomeRepository(private val scraper: HupuScraper) {
    suspend fun getPosts(): List<Post> = withContext(ioDispatcher) {
        scraper.fetchHome()
    }

    suspend fun getHotItems(): List<HotItem> = withContext(ioDispatcher) {
        scraper.fetchHot()
    }

    suspend fun getTopicThreads(tagId: Long, page: Int = 1): TopicThreadPage =
        withContext(ioDispatcher) {
            scraper.fetchTopicThreads(tagId, page)
        }
}
