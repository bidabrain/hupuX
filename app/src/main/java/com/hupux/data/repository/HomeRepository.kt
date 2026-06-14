package com.hupux.data.repository

import com.hupux.data.model.HotItem
import com.hupux.data.model.Post
import com.hupux.data.scraper.HupuScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeRepository @Inject constructor(private val scraper: HupuScraper) {
    suspend fun getPosts(): List<Post> = withContext(Dispatchers.IO) {
        scraper.fetchHome()
    }

    suspend fun getHotItems(): List<HotItem> = withContext(Dispatchers.IO) {
        scraper.fetchHot()
    }
}
