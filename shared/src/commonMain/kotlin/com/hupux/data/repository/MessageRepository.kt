package com.hupux.data.repository

import com.hupux.data.model.MessagePage
import com.hupux.data.scraper.HupuDesktopScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MessageRepository(private val scraper: HupuDesktopScraper) {
    suspend fun fetchMessages(tabKey: Int, pageStr: String? = null): MessagePage =
        withContext(Dispatchers.IO) { scraper.fetchMessages(tabKey, pageStr) }
}
