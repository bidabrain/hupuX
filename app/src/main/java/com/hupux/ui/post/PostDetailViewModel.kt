package com.hupux.ui.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hupux.data.local.FavoriteEntity
import com.hupux.data.model.Comment
import com.hupux.data.model.PostDetail
import com.hupux.data.repository.FavoritesRepository
import com.hupux.data.repository.PostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class PostDetailUiState {
    object Loading : PostDetailUiState()
    data class Success(
        val post: PostDetail,
        val isFavorite: Boolean,
        val subRepliesMap: Map<String, List<Comment>> = emptyMap(),
        val replyStack: List<String> = emptyList(),
        val isLoadingSubReplies: Boolean = false,
        val commentPage: Int = 1,
        val isLoadingMoreComments: Boolean = false,
        val replyingTo: Comment? = null,
        val showReplySheet: Boolean = false,
        val replyContent: String = "",
        val isSubmittingReply: Boolean = false,
        val replyError: String? = null,
        val replySuccess: Boolean = false
    ) : PostDetailUiState() {
        val expandedPid: String? get() = replyStack.lastOrNull()
    }
    data class Error(val message: String) : PostDetailUiState()
}

@HiltViewModel
class PostDetailViewModel @Inject constructor(
    private val postRepo: PostRepository,
    private val favRepo: FavoritesRepository
) : ViewModel() {

    private val _state = MutableStateFlow<PostDetailUiState>(PostDetailUiState.Loading)
    val state = _state.asStateFlow()
    private var currentTid = ""

    fun load(tid: String) {
        if (currentTid == tid && _state.value is PostDetailUiState.Success) return
        currentTid = tid
        viewModelScope.launch {
            _state.value = PostDetailUiState.Loading
            runCatching { postRepo.getPost(tid) }
                .onSuccess { post ->
                    val fav = favRepo.isFavorite(tid)
                    // 从初始评论列表建立父子关系索引
                    val initialMap = buildSubRepliesMap(post.comments)
                    _state.value = PostDetailUiState.Success(
                        post          = post,
                        isFavorite    = fav,
                        subRepliesMap = initialMap
                    )
                }
                .onFailure { _state.value = PostDetailUiState.Error(it.message ?: "加载失败") }
        }
    }

    /** 压栈：展开某条评论的子回复，不论当前在第几层都适用 */
    fun showReplies(parentPid: String) {
        val s = _state.value as? PostDetailUiState.Success ?: return
        _state.value = s.copy(
            replyStack          = s.replyStack + parentPid,
            isLoadingSubReplies = true
        )
        viewModelScope.launch {
            runCatching { postRepo.getSubReplies(currentTid, parentPid) }
                .onSuccess { subReplies ->
                    val s2 = _state.value as? PostDetailUiState.Success ?: return@onSuccess
                    val existing = s2.subRepliesMap[parentPid] ?: emptyList()
                    val merged   = (existing + subReplies)
                        .distinctBy { it.pid }
                        .sortedBy { it.pid.toLongOrNull() ?: 0L }
                    val newMap   = s2.subRepliesMap.toMutableMap()
                        .also { it[parentPid] = merged }
                    _state.value = s2.copy(subRepliesMap = newMap, isLoadingSubReplies = false)
                }
                .onFailure {
                    val s2 = _state.value as? PostDetailUiState.Success ?: return@onFailure
                    _state.value = s2.copy(isLoadingSubReplies = false)
                }
        }
    }

    fun loadMoreComments() {
        val s = _state.value as? PostDetailUiState.Success ?: return
        if (s.isLoadingMoreComments || !s.post.hasMoreComments) return
        _state.value = s.copy(isLoadingMoreComments = true)
        val nextPage = s.commentPage + 1
        viewModelScope.launch {
            runCatching { postRepo.loadMoreComments(currentTid, nextPage) }
                .onSuccess { result ->
                    val s2 = _state.value as? PostDetailUiState.Success ?: return@onSuccess
                    val seen = s2.post.comments.map { it.pid }.toHashSet()
                    val fresh = result.first.filter { it.pid !in seen }
                    _state.value = s2.copy(
                        post = s2.post.copy(comments = s2.post.comments + fresh, hasMoreComments = result.second),
                        commentPage = nextPage,
                        isLoadingMoreComments = false
                    )
                }
                .onFailure {
                    (_state.value as? PostDetailUiState.Success)?.let {
                        _state.value = it.copy(isLoadingMoreComments = false)
                    }
                }
        }
    }

    /** 返回上一层 */
    fun popReplies() {
        val s = _state.value as? PostDetailUiState.Success ?: return
        _state.value = s.copy(
            replyStack          = s.replyStack.dropLast(1),
            isLoadingSubReplies = false
        )
    }

    /** 关闭整个回复面板 */
    fun dismissReplies() {
        val s = _state.value as? PostDetailUiState.Success ?: return
        _state.value = s.copy(replyStack = emptyList())
    }

    fun startReply(comment: Comment) {
        val s = _state.value as? PostDetailUiState.Success ?: return
        _state.value = s.copy(showReplySheet = true, replyingTo = comment, replyContent = "", replyError = null, replySuccess = false)
    }

    fun startMainPostReply() {
        val s = _state.value as? PostDetailUiState.Success ?: return
        _state.value = s.copy(showReplySheet = true, replyingTo = null, replyContent = "", replyError = null, replySuccess = false)
    }

    fun dismissReply() {
        val s = _state.value as? PostDetailUiState.Success ?: return
        _state.value = s.copy(showReplySheet = false, replyingTo = null, replyContent = "", replyError = null)
    }

    fun updateReplyContent(text: String) {
        val s = _state.value as? PostDetailUiState.Success ?: return
        _state.value = s.copy(replyContent = text, replyError = null)
    }

    fun submitReply() {
        val s = _state.value as? PostDetailUiState.Success ?: return
        val comment = s.replyingTo   // null = 回复主贴
        val content = s.replyContent.trim()
        if (content.isEmpty()) {
            _state.value = s.copy(replyError = "请输入回复内容")
            return
        }
        _state.value = s.copy(isSubmittingReply = true, replyError = null)
        viewModelScope.launch {
            runCatching {
                postRepo.submitReply(
                    tid     = currentTid,
                    fid     = s.post.fid,
                    topicId = s.post.topicId,
                    quoteId = comment?.pid ?: "0",
                    content = "<p>$content</p>"
                )
            }.onSuccess {
                _state.value = (_state.value as? PostDetailUiState.Success)
                    ?.copy(isSubmittingReply = false, replySuccess = true)
                    ?: _state.value
            }.onFailure { e ->
                _state.value = (_state.value as? PostDetailUiState.Success)
                    ?.copy(isSubmittingReply = false, replyError = e.message ?: "回复失败")
                    ?: _state.value
            }
        }
    }

    fun toggleFavorite() {
        val s = _state.value as? PostDetailUiState.Success ?: return
        viewModelScope.launch {
            favRepo.toggle(
                FavoriteEntity(
                    tid      = currentTid,
                    title    = s.post.title,
                    url      = "https://m.hupu.com/bbs/$currentTid.html",
                    label    = s.post.topicName,
                    replies  = s.post.replies,
                    imageUrl = ""
                )
            )
            _state.value = s.copy(isFavorite = !s.isFavorite)
        }
    }

    private fun buildSubRepliesMap(comments: List<Comment>): Map<String, List<Comment>> =
        comments
            .filter { it.quotePid != null }
            .groupBy { it.quotePid!! }
}
