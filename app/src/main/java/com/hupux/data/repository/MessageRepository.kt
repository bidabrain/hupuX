package com.hupux.data.repository

import com.hupux.data.model.MessagePage
import com.hupux.data.scraper.HupuDesktopScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    private val scraper: HupuDesktopScraper
) {
    suspend fun fetchMessages(tabKey: Int, pageStr: String? = null): MessagePage =
        withContext(Dispatchers.IO) { scraper.fetchMessages(tabKey, pageStr) }
}
