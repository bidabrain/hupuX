package com.hupux.data.repository

import com.hupux.data.model.PostDetail
import com.hupux.data.scraper.HupuScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostRepository @Inject constructor(private val scraper: HupuScraper) {
    suspend fun getPost(tid: String): PostDetail = withContext(Dispatchers.IO) {
        scraper.fetchPost(tid)
    }

    suspend fun getSubReplies(tid: String, parentPid: String) = withContext(Dispatchers.IO) {
        scraper.fetchSubReplies(tid, parentPid)
    }
}
