package com.hupux.ui.score

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hupux.data.local.CookiePreferences
import com.hupux.data.model.ScoreComment
import com.hupux.data.model.ScoreItemDetail
import com.hupux.data.scraper.HupuMatchScraper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScoreItemUiState(
    val detail: ScoreItemDetail? = null,
    val comments: List<ScoreComment> = emptyList(),
    val cursor: Long = 0L,
    val hasMore: Boolean = false,
    val totalComments: Long = 0L,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    /** 打分 / 发评论进行中 */
    val submitting: Boolean = false,
    /** 一次性提示（提交结果），消费后清空 */
    val toast: String? = null,
    /** 正在回复的评论；null 表示发新评论 */
    val replyTo: ScoreComment? = null,
    /** 输入框内容。放在 state 里是为了「失败不清空、成功才清空」*/
    val draft: String = ""
)

class ScoreItemViewModel(
    private val scraper: HupuMatchScraper,
    private val cookiePrefs: CookiePreferences
) : ViewModel() {

    private val _state = MutableStateFlow(ScoreItemUiState())
    val state = _state.asStateFlow()

    private var bizType = ""
    private var bizNo = ""

    fun load(type: String, no: String) {
        if (bizType == type && bizNo == no && _state.value.detail != null) return
        bizType = type
        bizNo = no
        _state.value = ScoreItemUiState(isLoading = true)
        viewModelScope.launch {
            runCatching { scraper.fetchItemDetail(type, no) }
                .onSuccess { d ->
                    _state.update { it.copy(detail = d, isLoading = false) }
                    loadComments(reset = true)
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message ?: "加载失败") }
                }
        }
    }

    fun loadComments(reset: Boolean = false) {
        val s = _state.value
        if (s.isLoadingMore) return
        if (!reset && !s.hasMore) return
        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            runCatching { scraper.fetchComments(bizType, bizNo, if (reset) 0L else s.cursor) }
                .onSuccess { page ->
                    _state.update {
                        val merged =
                            if (reset) page.comments
                            else (it.comments + page.comments).distinctBy { c -> c.commentId }
                        it.copy(
                            comments      = merged,
                            cursor        = page.cursor,
                            hasMore       = page.hasMore,
                            totalComments = page.totalCount,
                            isLoadingMore = false
                        )
                    }
                }
                .onFailure { _state.update { it.copy(isLoadingMore = false) } }
        }
    }

    /** @param stars 1~5 星，接口要的是 10 分制，所以乘 2 */
    fun submitScore(stars: Int) {
        if (!requireLogin()) return
        _state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val err = scraper.saveScore(bizType, bizNo, stars * 2)
            finishWrite(err, if (err == null) "已打 ${stars * 2} 分" else err)
        }
    }

    fun cancelScore() {
        if (!requireLogin()) return
        _state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val err = scraper.deleteScore(bizType, bizNo)
            finishWrite(err, if (err == null) "已取消评分" else err)
        }
    }

    fun startReply(comment: ScoreComment?) {
        _state.update { it.copy(replyTo = comment) }
    }

    fun updateDraft(text: String) = _state.update { it.copy(draft = text) }

    /** 发布失败时保留输入内容，避免用户白打一遍 */
    fun publish() {
        val content = _state.value.draft.trim()
        if (content.isBlank()) return
        if (!requireLogin()) return
        val reply = _state.value.replyTo
        _state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val err = scraper.publishComment(
                bizType         = bizType,
                bizNo           = bizNo,
                content         = content,
                parentCommentId = reply?.commentId ?: "",
                subjectId       = reply?.subjectId ?: ""
            )
            if (err == null) _state.update { it.copy(replyTo = null, draft = "") }
            finishWrite(err, if (err == null) "已发布" else err)
        }
    }

    fun toggleLight(comment: ScoreComment) {
        if (!requireLogin()) return
        val on = !comment.hasLight
        // 乐观更新，失败再回滚
        updateComment(comment.commentId) {
            it.copy(hasLight = on, lightCount = (it.lightCount + if (on) 1 else -1).coerceAtLeast(0))
        }
        viewModelScope.launch {
            val err = scraper.lightComment(comment.subjectId, comment.commentId, on)
            if (err != null) {
                updateComment(comment.commentId) {
                    it.copy(hasLight = !on, lightCount = comment.lightCount)
                }
                _state.update { it.copy(toast = err) }
            }
        }
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }

    // ── 内部 ──────────────────────────────────────────────────────────────

    private fun requireLogin(): Boolean {
        if (cookiePrefs.isLoggedIn) return true
        _state.update { it.copy(toast = "请先在「我的」页登录") }
        return false
    }

    private fun updateComment(id: String, transform: (ScoreComment) -> ScoreComment) {
        _state.update { s ->
            s.copy(comments = s.comments.map { if (it.commentId == id) transform(it) else it })
        }
    }

    /** 写操作收尾：刷新详情与评论，让服务端结果成为唯一真相 */
    private fun finishWrite(err: String?, toast: String) {
        _state.update { it.copy(submitting = false, toast = toast) }
        if (err != null) return
        viewModelScope.launch {
            runCatching { scraper.fetchItemDetail(bizType, bizNo) }
                .onSuccess { d -> _state.update { it.copy(detail = d) } }
            loadComments(reset = true)
        }
    }
}
