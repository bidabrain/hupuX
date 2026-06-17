package com.hupux.data.repository

import android.net.Uri
import com.hupux.data.local.CookiePreferences
import com.hupux.data.model.Comment
import com.hupux.data.model.PostDetail
import com.hupux.data.scraper.HupuDesktopScraper
import com.hupux.data.scraper.HupuImageUploader
import com.hupux.data.scraper.HupuScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PostRepository constructor(
    private val scraper: HupuScraper,
    private val desktopScraper: HupuDesktopScraper,
    private val cookiePrefs: CookiePreferences,
    private val imageUploader: HupuImageUploader
) {
    suspend fun getPost(tid: String): PostDetail = withContext(Dispatchers.IO) {
        val post = scraper.fetchPost(tid)
        if (cookiePrefs.isLoggedIn) {
            try {
                val desktop = desktopScraper.fetchPostReplies(tid)
                post.copy(
                    comments          = desktop.comments,
                    hasMoreComments   = desktop.currentPage < desktop.totalPages,
                    desktopTotalPages = desktop.totalPages,
                    fid               = desktop.fid,
                    topicId           = desktop.topicId,
                    isRecommended     = desktop.isRecommended
                )
            } catch (_: Exception) {
                post
            }
        } else {
            post
        }
    }

    suspend fun loadMoreComments(tid: String, page: Int): Pair<List<Comment>, Boolean> =
        withContext(Dispatchers.IO) {
            if (cookiePrefs.isLoggedIn) {
                val desktop = desktopScraper.fetchPostReplies(tid, page)
                Pair(desktop.comments, desktop.currentPage < desktop.totalPages)
            } else {
                scraper.fetchReplyList(tid, page)
            }
        }

    suspend fun createThread(topicId: Int, title: String, content: String): Long =
        withContext(Dispatchers.IO) {
            desktopScraper.createThread(topicId, title, content)
        }

    suspend fun uploadImage(uri: Uri): String =
        withContext(Dispatchers.IO) { imageUploader.upload(uri) }

    suspend fun submitReply(
        tid: String, fid: String, topicId: String,
        quoteId: String, content: String
    ) = withContext(Dispatchers.IO) {
        desktopScraper.createReply(tid, fid, topicId, quoteId, content)
    }

    suspend fun toggleLikeComment(
        pid: String, tid: String, fid: String, isCurrentlyLiked: Boolean
    ) = withContext(Dispatchers.IO) {
        val puid = cookiePrefs.extractUid()?.toLongOrNull() ?: 0L
        val pidL = pid.toLongOrNull() ?: 0L
        val tidL = tid.toLongOrNull() ?: 0L
        val fidL = fid.toLongOrNull() ?: 0L
        if (isCurrentlyLiked) {
            desktopScraper.cancelLightReply(pidL, tidL, puid, fidL)
        } else {
            desktopScraper.lightReply(pidL, tidL, puid, fidL)
        }
    }

    suspend fun collectPost(tid: String, isCurrentlyCollected: Boolean) = withContext(Dispatchers.IO) {
        val tidL = tid.toLongOrNull() ?: 0L
        if (isCurrentlyCollected) desktopScraper.uncollectThread(tidL)
        else desktopScraper.collectThread(tidL)
    }

    suspend fun recommendPost(tid: String, fid: String, isCurrentlyRecommended: Boolean) =
        withContext(Dispatchers.IO) {
            val tidL = tid.toLongOrNull() ?: 0L
            val fidL = fid.toLongOrNull() ?: 0L
            val status = if (isCurrentlyRecommended) 0 else 1
            desktopScraper.recommendThread(tidL, fidL, status)
        }

    suspend fun getSubReplies(tid: String, parentPid: String) = withContext(Dispatchers.IO) {
        if (cookiePrefs.isLoggedIn) {
            desktopScraper.fetchDesktopSubReplies(tid, parentPid)
        } else {
            scraper.fetchSubReplies(tid, parentPid)
        }
    }
}
