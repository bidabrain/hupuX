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
        // 回复层级栈：每次点击"X 条回复"压栈，返回键弹栈
        val replyStack: List<String> = emptyList(),
        val isLoadingSubReplies: Boolean = false
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
