package com.hupux.data.repository

import com.hupux.data.model.MessagePage
import com.hupux.data.ioDispatcher
import com.hupux.data.scraper.HupuDesktopScraper
import kotlinx.coroutines.withContext

class MessageRepository(private val scraper: HupuDesktopScraper) {
    suspend fun fetchMessages(tabKey: Int, pageStr: String? = null): MessagePage =
        withContext(ioDispatcher) { scraper.fetchMessages(tabKey, pageStr) }
}
