package com.hupux.data.repository

import com.hupux.data.local.CookiePreferences
import com.hupux.data.model.PostDetail
import com.hupux.data.scraper.HupuDesktopScraper
import com.hupux.data.scraper.HupuScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostRepository @Inject constructor(
    private val scraper: HupuScraper,
    private val desktopScraper: HupuDesktopScraper,
    private val cookiePrefs: CookiePreferences
) {
    suspend fun getPost(tid: String): PostDetail = withContext(Dispatchers.IO) {
        val post = scraper.fetchPost(tid)
        if (cookiePrefs.isLoggedIn) {
            try {
                val desktop = desktopScraper.fetchPostReplies(tid)
                post.copy(
                    comments          = desktop.comments,
                    hasMoreComments   = desktop.currentPage < desktop.totalPages,
                    desktopBaseUrl    = desktop.baseUrl,
                    desktopTotalPages = desktop.totalPages,
                    fid               = desktop.fid,
                    topicId           = desktop.topicId
                )
            } catch (_: Exception) {
                post  // 桌面 API 失败时回退移动版
            }
        } else {
            post
        }
    }

    suspend fun submitReply(
        tid: String, fid: String, topicId: String,
        quoteId: String, content: String
    ) = withContext(Dispatchers.IO) {
        desktopScraper.createReply(tid, fid, topicId, quoteId, content)
    }

    suspend fun getSubReplies(tid: String, parentPid: String) = withContext(Dispatchers.IO) {
        if (cookiePrefs.isLoggedIn) {
            desktopScraper.fetchDesktopSubReplies(tid, parentPid)
        } else {
            scraper.fetchSubReplies(tid, parentPid)
        }
    }
}
